package com.example.workshop

import android.content.Context
import com.example.workshop.data.Attendance
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.AwayAlert
import com.example.workshop.data.AwayAlertRepository
import com.example.workshop.data.StaffRepository
import com.example.workshop.data.WorkshopDatabase
import com.example.workshop.data.WorkshopSettingsRepository
import com.example.workshop.data.WorkshopTime
import com.example.workshop.data.WorkshopWifiSession
import com.example.workshop.data.WorkshopWifiSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The workshop tracking logic, shared by the screens and
 * the background service (WorkshopTrackingService).
 *
 * Each cycle:
 *  1. Checks the workshop Wi-Fi (every 5 minutes, or
 *     straight away when asked).
 *  2. For the staff member logged in on this phone:
 *     starts / ends Wi-Fi sessions, uploads live working
 *     and workshop time, and raises away alerts.
 *  3. If the admin is logged in on this phone: checks
 *     every staff member and raises away alerts.
 */
object WorkshopTracker {

    const val WIFI_CHECK_INTERVAL_MILLIS = 5 * 60 * 1000L

    // Away thresholds, in minutes.
    const val AWAY_NOTIFY_STAFF = 15
    const val AWAY_ALERT_ADMIN = 30
    const val AWAY_ALERT_EVERYONE = 60

    private const val PREFS = "garage_session"
    private const val KEY_ADMIN = "admin_logged_in"
    private const val KEY_STAFF_ID = "staff_id"
    private const val KEY_STAFF_NAME = "staff_name"

    private const val NETWORK_TIMEOUT = 20_000L

    private val _wifiConnected = MutableStateFlow(false)
    val wifiConnected: StateFlow<Boolean> = _wifiConnected.asStateFlow()

    private val _requiredWifiName =
        MutableStateFlow(WorkshopSettingsRepository.DEFAULT_WIFI_NAME)
    val requiredWifiName: StateFlow<String> = _requiredWifiName.asStateFlow()

    private val mutex = Mutex()
    private var lastWifiCheckMillis = 0L
    private val raisedAlerts = mutableSetOf<String>()

    /* ---------------- login state ---------------- */

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    fun isAdminLoggedIn(context: Context) =
        prefs(context).getBoolean(KEY_ADMIN, false)

    fun setAdminLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().apply {
            if (loggedIn) putBoolean(KEY_ADMIN, true) else remove(KEY_ADMIN)
        }.apply()
    }

    fun loggedInStaffId(context: Context): Int? {
        val id = prefs(context).getInt(KEY_STAFF_ID, -1)
        return id.takeIf { it >= 0 }
    }

    fun loggedInStaffName(context: Context): String =
        prefs(context).getString(KEY_STAFF_NAME, "") ?: ""

    fun setStaffLoggedIn(context: Context, staffId: Int?, staffName: String = "") {
        prefs(context).edit().apply {
            if (staffId == null) {
                remove(KEY_STAFF_ID)
                remove(KEY_STAFF_NAME)
            } else {
                putInt(KEY_STAFF_ID, staffId)
                putString(KEY_STAFF_NAME, staffName)
            }
        }.apply()
    }

    fun anyoneLoggedIn(context: Context) =
        isAdminLoggedIn(context) || loggedInStaffId(context) != null

    /* ---------------- repositories ---------------- */

    private class Repos(context: Context) {
        val database = WorkshopDatabase.getDatabase(context)
        val settings = WorkshopSettingsRepository(context)
        val attendance = AttendanceRepository(database.attendanceDao())
        val sessions = WorkshopWifiSessionRepository(database.workshopWifiSessionDao())
        val staff = StaffRepository(database.staffDao())
        val alerts = AwayAlertRepository()
    }

    /* ---------------- Wi-Fi ---------------- */

    /*
     * Check the workshop Wi-Fi now. Also refreshes the
     * required Wi-Fi name set by the admin.
     */
    suspend fun checkWifiNow(context: Context): Boolean {

        val settings = WorkshopSettingsRepository(context)

        _requiredWifiName.value = settings.cachedWifiName()

        withTimeoutOrNull(NETWORK_TIMEOUT) {
            _requiredWifiName.value = settings.refreshWifiName()
        }

        val connected =
            WifiChecker.isOnWifi(context, _requiredWifiName.value)

        _wifiConnected.value = connected
        lastWifiCheckMillis = System.currentTimeMillis()

        return connected
    }

    fun setRequiredWifiName(name: String) {
        _requiredWifiName.value = name
    }

    /* ---------------- tracking cycle ---------------- */

    suspend fun runCycle(
        context: Context,
        forceWifiCheck: Boolean = false
    ): Unit = mutex.withLock {

        val appContext = context.applicationContext
        val repos = Repos(appContext)

        val wifiDue =
            System.currentTimeMillis() - lastWifiCheckMillis >=
                    WIFI_CHECK_INTERVAL_MILLIS

        if (forceWifiCheck || wifiDue) {
            checkWifiNow(appContext)
        }

        loggedInStaffId(appContext)?.let { staffId ->
            safely("staff tracking") {
                trackStaff(
                    appContext,
                    repos,
                    staffId,
                    loggedInStaffName(appContext)
                )
            }
        }

        if (isAdminLoggedIn(appContext)) {
            safely("admin watch") {
                watchAllStaff(repos)
            }
        }

        Unit
    }

    private suspend fun trackStaff(
        context: Context,
        repos: Repos,
        staffId: Int,
        staffName: String
    ) {

        val attendance =
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.attendance.getCurrentAttendance(staffId)
            } ?: return

        // Only track between check-in and check-out.
        if (attendance.checkInTime == null || attendance.checkOutTime != null) {
            return
        }

        val shiftDate = attendance.date
        val now = WorkshopTime.now()

        // Start or end the Wi-Fi session.
        val active =
            repos.sessions.getActiveSession(staffId, shiftDate)

        if (_wifiConnected.value && active == null) {
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.sessions.startSession(staffId, shiftDate, now)
            }
        } else if (!_wifiConnected.value && active != null) {
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.sessions.endSession(active, now)
            }
        }

        val sessions =
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.sessions.getSessionsForDate(staffId, shiftDate)
            } ?: return

        // Upload live totals for the admin dashboard.
        withTimeoutOrNull(NETWORK_TIMEOUT) {
            repos.attendance.updateLiveTimes(
                attendance = attendance,
                totalWorkingMinutes = totalWorkingMinutes(attendance),
                workshopMinutes = workshopMinutes(sessions, shiftDate)
            )
        }

        // Away checks.
        val awaySince = awaySince(attendance, sessions) ?: return
        val awayMinutes = WorkshopTime.minutesBetween(awaySince, null, shiftDate)

        if (awayMinutes >= AWAY_NOTIFY_STAFF) {
            notifyStaffOnce(context, staffId, awaySince)
        }

        raiseAlertsIfNeeded(repos, staffId, staffName, awaySince, awayMinutes)
    }

    private suspend fun watchAllStaff(repos: Repos) {

        val staffList =
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.staff.getAllStaff()
            } ?: return

        val attendanceList =
            withTimeoutOrNull(NETWORK_TIMEOUT) {
                repos.attendance.getAllTodayAttendance(WorkshopTime.today())
            } ?: return

        attendanceList
            .filter { it.checkInTime != null && it.checkOutTime == null }
            .forEach { attendance ->

                val sessions =
                    withTimeoutOrNull(NETWORK_TIMEOUT) {
                        repos.sessions.getSessionsForDate(
                            attendance.staffId,
                            attendance.date
                        )
                    } ?: return@forEach

                val awaySince = awaySince(attendance, sessions) ?: return@forEach

                val awayMinutes =
                    WorkshopTime.minutesBetween(awaySince, null, attendance.date)

                val name =
                    staffList.firstOrNull { it.id == attendance.staffId }?.name
                        ?: "Staff ${attendance.staffId}"

                raiseAlertsIfNeeded(
                    repos,
                    attendance.staffId,
                    name,
                    awaySince,
                    awayMinutes
                )
            }
    }

    private suspend fun raiseAlertsIfNeeded(
        repos: Repos,
        staffId: Int,
        staffName: String,
        awaySince: String,
        awayMinutes: Long
    ) {

        listOf(AWAY_ALERT_ADMIN, AWAY_ALERT_EVERYONE)
            .filter { awayMinutes >= it }
            .forEach { level ->

                val key = "${staffId}_${awaySince}_$level"

                if (key in raisedAlerts) {
                    return@forEach
                }

                val done =
                    withTimeoutOrNull(NETWORK_TIMEOUT) {
                        repos.alerts.raiseAlert(
                            staffId = staffId,
                            staffName = staffName,
                            date = WorkshopTime.today(),
                            awaySince = awaySince,
                            level = level
                        )
                        true
                    }

                if (done == true) {
                    raisedAlerts += key
                }
            }
    }

    private fun notifyStaffOnce(
        context: Context,
        staffId: Int,
        awaySince: String
    ) {

        val key = "away15_${staffId}_$awaySince"
        val prefs = prefs(context)

        if (prefs.getBoolean(key, false)) {
            return
        }

        Notifier.showAlert(
            context = context,
            id = key.hashCode(),
            title = "You're away from the workshop",
            text =
                "You've been off the workshop Wi-Fi for $AWAY_NOTIFY_STAFF minutes " +
                        "(since ${WorkshopTime.display(awaySince)}). " +
                        "Please return to the workshop."
        )

        prefs.edit().putBoolean(key, true).apply()
    }

    /*
     * Called by the background service for each new
     * alert in Firestore. Decides who sees it:
     *  - 30 minutes: the admin
     *  - 60 minutes: the admin and every logged-in staff member
     */
    fun onAlertReceived(context: Context, alert: AwayAlert) {

        val isAdmin = isAdminLoggedIn(context)
        val staffId = loggedInStaffId(context)

        val shouldShow =
            when (alert.level) {
                AWAY_ALERT_ADMIN -> isAdmin
                AWAY_ALERT_EVERYONE -> isAdmin || staffId != null
                else -> false
            }

        if (!shouldShow) {
            return
        }

        val duration =
            if (alert.level >= AWAY_ALERT_EVERYONE) "over an hour" else "over 30 minutes"

        val since = WorkshopTime.display(alert.awaySince)

        val (title, text) =
            if (!isAdmin && staffId == alert.staffId) {
                "You're away from the workshop" to
                        "You've been away from the workshop for $duration (since $since)."
            } else {
                "${alert.staffName} is away" to
                        "${alert.staffName} has been away from the workshop for $duration (since $since)."
            }

        Notifier.showAlert(context, alert.id.hashCode(), title, text)
    }

    /* ---------------- calculations ---------------- */

    fun totalWorkingMinutes(attendance: Attendance?): Long {

        if (attendance?.checkInTime == null) {
            return 0L
        }

        return WorkshopTime.minutesBetween(
            attendance.checkInTime,
            attendance.checkOutTime,
            attendance.date
        )
    }

    /*
     * Time in the workshop = the sum of all of the shift's
     * Wi-Fi sessions. A session still open counts up to now.
     */
    fun workshopMinutes(
        sessions: List<WorkshopWifiSession>,
        shiftDate: String
    ): Long {
        return sessions.sumOf { session ->
            WorkshopTime.minutesBetween(
                session.connectedAt,
                session.disconnectedAt,
                shiftDate
            )
        }
    }

    /*
     * When a checked-in staff member left the workshop
     * Wi-Fi, or null if they are in the workshop now
     * (or not on shift).
     */
    fun awaySince(
        attendance: Attendance?,
        sessions: List<WorkshopWifiSession>
    ): String? {

        if (attendance?.checkInTime == null || attendance.checkOutTime != null) {
            return null
        }

        if (sessions.isEmpty() || sessions.any { it.disconnectedAt == null }) {
            return null
        }

        return sessions
            .mapNotNull { it.disconnectedAt }
            .maxByOrNull {
                WorkshopTime.parse(it, attendance.date)?.time ?: 0L
            }
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("WORKSHOP_TRACKER", "Failed: $what", e)
        }
    }
}
