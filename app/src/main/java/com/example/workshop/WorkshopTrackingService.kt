package com.example.workshop

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.workshop.data.AwayAlertRepository
import com.example.workshop.data.WorkshopTime
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * Keeps workshop tracking running while the app is in
 * the background or the screen is off:
 *  - Wi-Fi check every 5 minutes
 *  - live working / workshop time every minute
 *  - away-from-workshop alerts
 *
 * Runs as a foreground service (Android shows a small
 * "tracking" notification) while an admin or staff
 * member is logged in on this phone.
 */
class WorkshopTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val alertRepository = AwayAlertRepository()
    private var alertListener: ListenerRegistration? = null
    private var alertListenerDate: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        Notifier.createChannels(this)

        val notification =
            NotificationCompat.Builder(this, Notifier.TRACKING_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Nethaji Auto Garage")
                .setContentText("Tracking workshop time")
                .setOngoing(true)
                .setContentIntent(Notifier.openAppIntent(this))
                .build()

        try {
            ServiceCompat.startForeground(
                this,
                TRACKING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            // Usually: location permission not granted yet.
            android.util.Log.w("WORKSHOP_SERVICE", "Can't start in background", e)
            isRunning = false
            stopSelf()
            return
        }

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (!isRunning) {
            return START_NOT_STICKY
        }

        if (loopJob?.isActive != true) {
            loopJob = scope.launch { trackingLoop() }
        } else {
            // Asked to refresh now (login, check-in, …).
            scope.launch {
                WorkshopTracker.runCycle(this@WorkshopTrackingService, forceWifiCheck = true)
            }
        }

        return START_STICKY
    }

    private suspend fun trackingLoop() {

        var first = true

        while (scope.isActive) {

            if (!WorkshopTracker.anyoneLoggedIn(this)) {
                stopSelf()
                return
            }

            listenForAlerts()

            WorkshopTracker.runCycle(this, forceWifiCheck = first)
            first = false

            delay(CYCLE_MILLIS)
        }
    }

    /*
     * Listen to today's away alerts in Firestore.
     * Re-registers after midnight.
     */
    private fun listenForAlerts() {

        val today = WorkshopTime.today()

        if (alertListener != null && alertListenerDate == today) {
            return
        }

        alertListener?.remove()

        alertListener =
            alertRepository.listen(today) { alert ->
                WorkshopTracker.onAlertReceived(this, alert)
            }

        alertListenerDate = today
    }

    override fun onDestroy() {
        isRunning = false
        alertListener?.remove()
        alertListener = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {

        private const val TRACKING_NOTIFICATION_ID = 2001
        private const val CYCLE_MILLIS = 60_000L

        @Volatile
        var isRunning = false
            private set

        /*
         * Start the service if someone is logged in,
         * stop it otherwise. Call from the app while it
         * is on screen.
         */
        fun sync(context: Context) {

            val intent = Intent(context, WorkshopTrackingService::class.java)

            if (WorkshopTracker.anyoneLoggedIn(context) &&
                WifiChecker.hasPermission(context)
            ) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    android.util.Log.w("WORKSHOP_SERVICE", "Start failed", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
