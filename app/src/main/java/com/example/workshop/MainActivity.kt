package com.example.workshop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.workshop.data.AdminRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.example.workshop.data.Staff
import com.example.workshop.data.StaffRepository
import com.example.workshop.data.Attendance
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.ChecklistRepository
import com.example.workshop.data.WorkshopSettingsRepository
import com.example.workshop.data.WorkshopTime
import com.example.workshop.data.WorkshopWifiSessionRepository
import com.example.workshop.data.WorkshopDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The daily workshop checklist shown to staff.
 */
val WORKSHOP_CHECKLIST = listOf(
    "Shed Opened",
    "Cleaning",
    "Sorted Bikes",
    "Sorted Wrenches",
    "Engine Assembled",
    "Bore Assembled",
    "Engine Disassembled"
)

class MainActivity : ComponentActivity() {

    private lateinit var database: WorkshopDatabase
    private lateinit var adminRepository: AdminRepository
    private lateinit var staffRepository: StaffRepository

    lateinit var attendanceRepository: AttendanceRepository

    lateinit var workshopWifiSessionRepository: WorkshopWifiSessionRepository

    lateinit var settingsRepository: WorkshopSettingsRepository

    lateinit var checklistRepository: ChecklistRepository

    private val adminViewModel: AdminViewModel by viewModels {
        AdminViewModelFactory(adminRepository)
    }

    private val attendanceViewModel: AttendanceViewModel by viewModels {
        AttendanceViewModelFactory(attendanceRepository, workshopWifiSessionRepository)
    }

    private val workshopWifiSessionViewModel:
            WorkshopWifiSessionViewModel by viewModels {
        WorkshopWifiSessionViewModelFactory(
            workshopWifiSessionRepository
        )
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            lifecycleScope.launch {
                WorkshopTracker.runCycle(
                    this@MainActivity,
                    forceWifiCheck = true
                )
            }
            WorkshopTrackingService.sync(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        Notifier.createChannels(this)

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    android.util.Log.w(
                        "GARAGE_FCM",
                        "Fetching FCM token failed",
                        task.exception
                    )
                    return@addOnCompleteListener
                }

                android.util.Log.d(
                    "GARAGE_FCM",
                    "FCM registration token: ${task.result}"
                )
            }

        database = WorkshopDatabase.getDatabase(this)

        adminRepository = AdminRepository(
            database.adminDao()
        )

        staffRepository = StaffRepository(
            database.staffDao()
        )

        attendanceRepository = AttendanceRepository(
            database.attendanceDao()
        )

        workshopWifiSessionRepository =
            WorkshopWifiSessionRepository(
                database.workshopWifiSessionDao()
            )

        settingsRepository = WorkshopSettingsRepository(this)

        checklistRepository = ChecklistRepository(
            database.checklistDao()
        )

        requestPermissionsIfNeeded()

        setContent {
            MaterialTheme {
                WorkshopApp(
                    activity = this,
                    adminViewModel = adminViewModel,
                    staffRepository = staffRepository,
                    attendanceViewModel = attendanceViewModel,
                    workshopWifiSessionViewModel = workshopWifiSessionViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start background tracking if someone is logged in.
        WorkshopTrackingService.sync(this)
    }

    /*
     * Wi-Fi name needs location (and nearby Wi-Fi on
     * Android 13+). Alerts need notification permission
     * on Android 13+.
     */
    fun requestPermissionsIfNeeded() {

        val needed = mutableListOf<String>()

        if (!WifiChecker.hasPermission(this)) {
            // Android 12+ ignores a fine-location request
            // that doesn't include coarse location too.
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                needed += Manifest.permission.NEARBY_WIFI_DEVICES
            }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifier.canNotify(this)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

/* ============================================================
   SHARED LAYOUT
   ============================================================ */

/*
 * A screen that is centred when it fits and scrolls
 * when it doesn't (small phones, keyboard open).
 */
@Composable
fun CenteredScrollColumn(
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/* ============================================================
   MAIN APPLICATION
   ============================================================ */

@Composable
fun WorkshopApp(
    activity: MainActivity,
    adminViewModel: AdminViewModel,
    staffRepository: StaffRepository,
    attendanceViewModel: AttendanceViewModel,
    workshopWifiSessionViewModel: WorkshopWifiSessionViewModel
) {

    val scope = rememberCoroutineScope()

    val wifiConnected by
    WorkshopTracker.wifiConnected.collectAsState()

    val requiredWifiName by
    WorkshopTracker.requiredWifiName.collectAsState()

    /*
     * A staff login is remembered on the phone so that
     * tracking keeps going in the background and after
     * the app is reopened.
     */
    var loggedInStaff by remember {
        mutableStateOf(
            WorkshopTracker.loggedInStaffId(activity)?.let { id ->
                Staff(
                    id = id,
                    name = WorkshopTracker.loggedInStaffName(activity),
                    username = "",
                    password = ""
                )
            }
        )
    }

    var screen by remember {
        mutableStateOf(
            when {
                WorkshopTracker.isAdminLoggedIn(activity) -> "adminDashboard"
                loggedInStaff != null -> "staffHome"
                else -> "home"
            }
        )
    }

    BackHandler(enabled = screen != "home") {
        screen = when (screen) {
            "adminLogin" -> "home"
            "staffLogin" -> "home"
            "adminDashboard" -> "home"
            "staffManagement" -> "adminDashboard"
            "staffHome" -> "staffHome"
            else -> "home"
        }
    }

    fun refreshNow() {
        scope.launch {
            WorkshopTracker.runCycle(activity, forceWifiCheck = true)
            loggedInStaff?.let {
                attendanceViewModel.loadTodayAttendance(it.id)
            }
        }
    }

    /*
     * Check Wi-Fi when the app opens. Then, if background
     * tracking couldn't start (for example, a permission
     * was refused), keep tracking while the app is open:
     * every minute, with the Wi-Fi checked every 5 minutes.
     */
    LaunchedEffect(Unit) {

        WorkshopTracker.runCycle(activity, forceWifiCheck = true)

        while (true) {
            delay(60_000L)

            if (!WorkshopTrackingService.isRunning) {
                WorkshopTracker.runCycle(activity)
            }
        }
    }

    when (screen) {

        "home" -> {

            HomeScreen(
                wifiConnected = wifiConnected,

                onRefreshWifi = {
                    activity.requestPermissionsIfNeeded()
                    refreshNow()
                },

                onAdminLogin = {
                    screen =
                        if (WorkshopTracker.isAdminLoggedIn(activity)) {
                            "adminDashboard"
                        } else {
                            "adminLogin"
                        }
                },

                onStaffLogin = {
                    screen =
                        if (loggedInStaff != null) "staffHome" else "staffLogin"
                }
            )
        }

        "adminLogin" -> {

            AdminLoginScreen(
                adminViewModel = adminViewModel,

                onBack = {
                    screen = "home"
                },

                onSuccess = {
                    WorkshopTracker.setAdminLoggedIn(activity, true)
                    WorkshopTrackingService.sync(activity)
                    screen = "adminDashboard"
                }
            )
        }

        "staffLogin" -> {

            StaffLoginScreen(
                staffRepository = staffRepository,

                onBack = {
                    screen = "home"
                },

                onLoginSuccess = { staff ->
                    loggedInStaff = staff
                    WorkshopTracker.setStaffLoggedIn(activity, staff.id, staff.name)
                    WorkshopTrackingService.sync(activity)
                    refreshNow()
                    screen = "staffHome"
                }
            )
        }

        "staffHome" -> {

            val staff = loggedInStaff

            if (staff == null) {
                LaunchedEffect(Unit) { screen = "home" }
            } else {

                StaffHomeScreen(
                    staff = staff,
                    attendanceViewModel = attendanceViewModel,
                    workshopWifiSessionViewModel =
                        workshopWifiSessionViewModel,
                    checklistRepository = activity.checklistRepository,
                    wifiConnected = wifiConnected,
                    onRefreshWifi = { refreshNow() },
                    onAttendanceChanged = { refreshNow() },
                    onLogout = {
                        loggedInStaff = null
                        WorkshopTracker.setStaffLoggedIn(activity, null)
                        attendanceViewModel.clear()
                        WorkshopTrackingService.sync(activity)
                        screen = "home"
                    }
                )
            }
        }

        "adminDashboard" -> {
            AdminDashboardScreen(
                staffRepository = staffRepository,
                attendanceRepository = activity.attendanceRepository,
                workshopWifiSessionRepository =
                    activity.workshopWifiSessionRepository,
                settingsRepository = activity.settingsRepository,
                checklistRepository = activity.checklistRepository,
                requiredWifiName = requiredWifiName,
                currentWifiName = {
                    WifiChecker.currentSsid(activity)
                },
                onWifiNameSaved = { name ->
                    WorkshopTracker.setRequiredWifiName(name)
                    refreshNow()
                },
                onOpenStaffManagement = {
                    screen = "staffManagement"
                },
                onLogout = {
                    WorkshopTracker.setAdminLoggedIn(activity, false)
                    WorkshopTrackingService.sync(activity)
                    screen = "home"
                }
            )
        }

        "staffManagement" -> {
            StaffManagementScreen(
                staffRepository = staffRepository,
                onBack = {
                    screen = "adminDashboard"
                }
            )
        }
    }
}

/* ============================================================
   HOME SCREEN
   ============================================================ */

@Composable
fun HomeScreen(
    wifiConnected: Boolean,
    onRefreshWifi: () -> Unit,
    onAdminLogin: () -> Unit,
    onStaffLogin: () -> Unit
) {

    CenteredScrollColumn {

        Text(
            text = "Nethaji Auto Garage",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Workshop Attendance System",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Wi-Fi Status",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text =
                        if (wifiConnected)
                            "🟢 Workshop Wi-Fi Connected"
                        else
                            "🔴 Workshop Wi-Fi Not Connected"
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onAdminLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Admin Login")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onStaffLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Staff Login")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onRefreshWifi
        ) {
            Text("Refresh Wi-Fi Status")
        }
    }
}

/* ============================================================
   ADMIN LOGIN
   ============================================================ */

@Composable
fun AdminLoginScreen(
    adminViewModel: AdminViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    val loginResult by
    adminViewModel.loginResult.collectAsState()

    LaunchedEffect(loginResult) {

        if (loginResult != null) {
            onSuccess()
            adminViewModel.clearLoginResult()
        }
    }

    CenteredScrollColumn {

        Text(
            text = "Admin Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = ""
            },
            label = {
                Text("Username")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = ""
            },
            label = {
                Text("Password")
            },
            visualTransformation =
                PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {

                if (
                    username.isBlank() ||
                    password.isBlank()
                ) {

                    errorMessage =
                        "Please enter username and password"

                } else {

                    adminViewModel.login(
                        username,
                        password
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        if (errorMessage.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = errorMessage
            )
        }

        if (
            loginResult == null &&
            username.isNotBlank() &&
            password.isNotBlank()
        ) {

            /*
             * Login result is handled by LaunchedEffect.
             * If it remains null after login, show nothing here.
             */
        }
    }
}

/* ============================================================
   STAFF LOGIN
   ============================================================ */

@Composable
fun StaffLoginScreen(
    staffRepository: StaffRepository,
    onBack: () -> Unit,
    onLoginSuccess: (Staff) -> Unit
) {
    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var success by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    CenteredScrollColumn {

        Text(
            text = "Staff Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                message = ""
            },
            label = {
                Text("Username")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                message = ""
            },
            label = {
                Text("Password")
            },
            visualTransformation =
                PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {

                if (
                    username.isBlank() ||
                    password.isBlank()
                ) {

                    message =
                        "Please enter username and password"

                } else {

                    scope.launch {

                        val staff =
                            staffRepository.login(
                                username,
                                password
                            )

                        if (staff != null) {

                            success = true

                            message =
                                "Login successful: ${staff.name}"

                            onLoginSuccess(staff)

                        } else {

                            success = false

                            message =
                                "Invalid username or password"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        if (message.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = message,
                color =
                    if (success)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
            )
        }
    }
}

/* ============================================================
   ADMIN DASHBOARD
   ============================================================ */

@Composable
fun StaffManagementScreen(
    staffRepository: StaffRepository,
    onBack: () -> Unit
) {

    var staffList by remember {
        mutableStateOf<List<Staff>>(emptyList())
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var editingStaff by remember {
        mutableStateOf<Staff?>(null)
    }

    var message by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    fun loadStaff() {

        scope.launch {

            staffList =
                staffRepository.getAllStaff()
        }
    }

    LaunchedEffect(Unit) {
        loadStaff()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "Staff Management",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedButton(
                onClick = onBack
            ) {
                Text("Back")
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {
                showAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Staff")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = {
                loadStaff()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Staff")
        }

        if (message.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(message)
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Staff Members: ${staffList.size}",
            style =
                MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            items(
                items = staffList,
                key = {
                    it.id
                }
            ) { staff ->




                StaffCard(
                    staff = staff,

                    onEdit = {
                        editingStaff = staff
                    },

                    onToggleActive = {

                        scope.launch {

                            staffRepository.updateStaff(
                                staff.copy(
                                    active = !staff.active
                                )
                            )

                            loadStaff()

                            message =
                                if (!staff.active)
                                    "${staff.name} activated"
                                else
                                    "${staff.name} deactivated"
                        }
                    },

                    onDelete = {

                        scope.launch {

                            staffRepository.deleteStaff(
                                staff
                            )

                            loadStaff()

                            message =
                                "${staff.name} deleted"
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }
        }
    }

    /* ADD STAFF DIALOG */

    if (showAddDialog) {

        AddStaffDialog(

            onDismiss = {
                showAddDialog = false
            },

            onSave = { name, username, password ->

                scope.launch {

                    staffRepository.addStaff(
                        Staff(
                            name = name,
                            username = username,
                            password = password,
                            active = true
                        )
                    )

                    showAddDialog = false

                    loadStaff()

                    message =
                        "Staff added successfully"
                }
            }
        )
    }

    /* EDIT STAFF DIALOG */

    editingStaff?.let { staff ->

        EditStaffDialog(

            staff = staff,

            onDismiss = {
                editingStaff = null
            },

            onSave = { updatedStaff ->

                scope.launch {

                    staffRepository.updateStaff(
                        updatedStaff
                    )

                    editingStaff = null

                    loadStaff()

                    message =
                        "Staff updated successfully"
                }
            }
        )
    }
}


/* ============================================================
   ADMIN DASHBOARD
   ============================================================ */

/*
 * What the dashboard shows for one staff member today.
 */
data class StaffDayStatus(
    val workshopMinutes: Long,
    val awaySince: String?,
    val checklistDone: Int
)

@Composable
fun AdminDashboardScreen(
    staffRepository: StaffRepository,
    attendanceRepository: AttendanceRepository,
    workshopWifiSessionRepository: WorkshopWifiSessionRepository,
    settingsRepository: WorkshopSettingsRepository,
    checklistRepository: ChecklistRepository,
    requiredWifiName: String,
    currentWifiName: () -> String?,
    onWifiNameSaved: (String) -> Unit,
    onOpenStaffManagement: () -> Unit,
    onLogout: () -> Unit
) {

    var staffList by remember {
        mutableStateOf<List<Staff>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var todayAttendance by remember {
        mutableStateOf<List<Attendance>>(emptyList())
    }

    var statusByStaff by remember {
        mutableStateOf<Map<Int, StaffDayStatus>>(emptyMap())
    }

    var attendanceLoading by remember {
        mutableStateOf(true)
    }

    var attendanceError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                staffList = staffRepository.getAllStaff()

                val today = WorkshopTime.today()

                todayAttendance =
                    attendanceRepository.getAllTodayAttendance(today)

                val attendanceByStaffId =
                    todayAttendance.associateBy { it.staffId }

                statusByStaff = staffList.associate { staff ->

                    val attendance = attendanceByStaffId[staff.id]

                    // A shift that started yesterday keeps
                    // yesterday's date for its sessions.
                    val shiftDate = attendance?.date ?: today

                    val sessions =
                        workshopWifiSessionRepository.getSessionsForDate(
                            staffId = staff.id,
                            date = shiftDate
                        )

                    val checklist =
                        checklistRepository.getChecklist(staff.id, shiftDate)

                    staff.id to StaffDayStatus(
                        // Sum of all of the shift's Wi-Fi sessions.
                        workshopMinutes =
                            WorkshopTracker.workshopMinutes(sessions, shiftDate),
                        awaySince =
                            WorkshopTracker.awaySince(attendance, sessions),
                        checklistDone =
                            WORKSHOP_CHECKLIST.count { checklist[it] == true }
                    )
                }

                attendanceError = null
            } catch (e: Exception) {
                attendanceError =
                    e.message ?: "Unable to load attendance"
            } finally {
                loading = false
                attendanceLoading = false
            }

            delay(5_000)
        }
    }

    val activeStaff = staffList.count { it.active }
    val inactiveStaff = staffList.count { !it.active }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(all = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Admin Dashboard",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedButton(
                onClick = onLogout
            ) {
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Nethaji Auto Garage",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Garage Overview",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (loading) {
            Text("Loading staff information...")
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Total Staff")
                    Text(
                        text = "${staffList.size}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Active")
                        Text(
                            text = "$activeStaff",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Inactive")
                        Text(
                            text = "$inactiveStaff",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Today's Attendance",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                val attendanceByStaffId =
                    todayAttendance.associateBy { it.staffId }

                val currentlyWorking = todayAttendance.count {
                    it.checkInTime != null && it.checkOutTime == null
                }

                val checkedOut = todayAttendance.count {
                    it.checkOutTime != null
                }

                val awayNow = statusByStaff.values.count { it.awaySince != null }

                Text(
                    text = "Attendance Summary",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (attendanceLoading) {
                    Text("Loading attendance...")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Checked in")
                            Text(
                                text = "${todayAttendance.size}",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Column {
                            Text("Working")
                            Text(
                                text = "$currentlyWorking",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Column {
                            Text("Away")
                            Text(
                                text = "$awayNow",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Column {
                            Text("Checked out")
                            Text(
                                text = "$checkedOut",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Staff Details",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (staffList.isEmpty()) {
                        Text("No staff members found.")
                    } else {
                        staffList.forEach { staff ->
                            val attendance = attendanceByStaffId[staff.id]
                            val dayStatus = statusByStaff[staff.id]

                            val status = when {
                                attendance == null -> "Not checked in"
                                attendance.checkOutTime != null -> "Checked out"
                                dayStatus?.awaySince != null ->
                                    "Away from workshop for " +
                                            WorkshopTime.formatMinutes(
                                                WorkshopTime.minutesBetween(
                                                    dayStatus.awaySince,
                                                    null,
                                                    attendance.date
                                                )
                                            )
                                attendance.checkInTime != null -> "Currently working"
                                else -> "Not checked in"
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = staff.name,
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text("Status: $status")
                                Text(
                                    "Check-in: ${
                                        WorkshopTime.display(
                                            attendance?.checkInTime,
                                            attendance?.date
                                        )
                                    }"
                                )
                                Text(
                                    "Check-out: ${
                                        WorkshopTime.display(
                                            attendance?.checkOutTime,
                                            attendance?.date
                                        )
                                    }"
                                )
                                Text(
                                    "Total working time: ${
                                        WorkshopTime.formatMinutes(
                                            WorkshopTracker.totalWorkingMinutes(attendance)
                                        )
                                    }"
                                )
                                Text(
                                    "Time in workshop: ${
                                        WorkshopTime.formatMinutes(
                                            dayStatus?.workshopMinutes ?: 0L
                                        )
                                    }"
                                )
                                Text(
                                    "Checklist: ${dayStatus?.checklistDone ?: 0} of ${WORKSHOP_CHECKLIST.size} done"
                                )
                            }
                        }
                    }

                    attendanceError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Attendance error: $error")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        WorkshopWifiSettingsCard(
            settingsRepository = settingsRepository,
            requiredWifiName = requiredWifiName,
            currentWifiName = currentWifiName,
            onSaved = onWifiNameSaved
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onOpenStaffManagement,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔧  Staff Management")
        }
    }
}

/* ============================================================
   WORKSHOP WI-FI SETTING (ADMIN)
   ============================================================ */

@Composable
fun WorkshopWifiSettingsCard(
    settingsRepository: WorkshopSettingsRepository,
    requiredWifiName: String,
    currentWifiName: () -> String?,
    onSaved: (String) -> Unit
) {

    var newName by remember(requiredWifiName) {
        mutableStateOf(requiredWifiName)
    }

    var message by remember {
        mutableStateOf("")
    }

    var saving by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Workshop Wi-Fi",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Staff must be on this Wi-Fi to check in and out.")

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Current: $requiredWifiName",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it
                    message = ""
                },
                label = {
                    Text("Wi-Fi name")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val ssid = currentWifiName()
                    if (ssid != null) {
                        newName = ssid
                        message = ""
                    } else {
                        message =
                            "Can't read this phone's Wi-Fi name. Connect to Wi-Fi and allow location."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use this phone's current Wi-Fi")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = !saving && newName.isNotBlank() && newName.trim() != requiredWifiName,
                onClick = {
                    saving = true
                    scope.launch {
                        message =
                            try {
                                settingsRepository.saveWifiName(newName)
                                onSaved(newName.trim())
                                "Saved. All phones will use \"${newName.trim()}\"."
                            } catch (e: Exception) {
                                "Couldn't save: ${e.message ?: "no connection"}"
                            }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Saving..." else "Save Wi-Fi Name")
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(message)
            }
        }
    }
}

/* ============================================================
   STAFF CARD
   ============================================================ */

@Composable
fun StaffCard(
    staff: Staff,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = staff.name,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Username: ${staff.username}"
            )

            Text(
                text =
                    if (staff.active)
                        "Status: Active"
                    else
                        "Status: Inactive"
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = onEdit
                ) {
                    Text("Edit")
                }

                OutlinedButton(
                    onClick = onToggleActive
                ) {
                    Text(
                        if (staff.active)
                            "Disable"
                        else
                            "Activate"
                    )
                }

                OutlinedButton(
                    onClick = onDelete
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

/* ============================================================
   ADD STAFF DIALOG
   ============================================================ */

@Composable
fun AddStaffDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        username: String,
        password: String
    ) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Add Staff")
        },

        text = {

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("Staff Name")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = {
                        Text("Username")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = {
                        Text("Password")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotEmpty()) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = error,
                        color =
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },

        confirmButton = {

            Button(
                onClick = {

                    if (
                        name.isBlank() ||
                        username.isBlank() ||
                        password.isBlank()
                    ) {

                        error =
                            "All fields are required"

                    } else {

                        onSave(
                            name.trim(),
                            username.trim(),
                            password
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },

        dismissButton = {

            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

/* ============================================================
   EDIT STAFF DIALOG
   ============================================================ */

@Composable
fun EditStaffDialog(
    staff: Staff,
    onDismiss: () -> Unit,
    onSave: (Staff) -> Unit
) {

    var name by remember {
        mutableStateOf(staff.name)
    }

    var username by remember {
        mutableStateOf(staff.username)
    }

    var password by remember {
        mutableStateOf(staff.password)
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Edit Staff")
        },

        text = {

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("Staff Name")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = {
                        Text("Username")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = {
                        Text("Password")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },

        confirmButton = {

            Button(
                onClick = {

                    onSave(
                        staff.copy(
                            name = name.trim(),
                            username = username.trim(),
                            password = password
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },

        dismissButton = {

            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

/* ============================================================
   STAFF DASHBOARD
   ============================================================ */

@Composable
fun StaffHomeScreen(
    staff: Staff,
    attendanceViewModel: AttendanceViewModel,
    workshopWifiSessionViewModel:
    WorkshopWifiSessionViewModel,
    checklistRepository: ChecklistRepository,
    wifiConnected: Boolean,
    onRefreshWifi: () -> Unit,
    onAttendanceChanged: () -> Unit,
    onLogout: () -> Unit
) {

    val attendance by
    attendanceViewModel.todayAttendance.collectAsState()

    var totalWorkingMinutes by remember {
        mutableStateOf(0L)
    }

    var workshopMinutes by remember {
        mutableStateOf(0L)
    }

    var awaySince by remember {
        mutableStateOf<String?>(null)
    }

    // The checklist belongs to the shift's day.
    val checklistDate = attendance?.date ?: WorkshopTime.today()

    var checklist by remember(staff.id, checklistDate) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(staff.id) {
        attendanceViewModel.loadTodayAttendance(staff.id)
    }

    LaunchedEffect(staff.id, checklistDate) {
        checklist = checklistRepository.getChecklist(staff.id, checklistDate)
    }

    /*
     * Refresh the times on screen every minute.
     * (Saving them to Firebase is done by WorkshopTracker,
     * also in the background.)
     */
    LaunchedEffect(
        staff.id,
        wifiConnected,
        attendance?.checkInTime,
        attendance?.checkOutTime
    ) {

        while (true) {

            val current = attendance

            totalWorkingMinutes =
                WorkshopTracker.totalWorkingMinutes(current)

            if (current != null) {

                val sessions =
                    workshopWifiSessionViewModel.getShiftSessions(
                        staffId = staff.id,
                        shiftDate = current.date
                    )

                // Sum of all of the shift's Wi-Fi sessions.
                workshopMinutes =
                    WorkshopTracker.workshopMinutes(sessions, current.date)

                awaySince =
                    WorkshopTracker.awaySince(current, sessions)
            } else {
                workshopMinutes = 0L
                awaySince = null
            }

            delay(60_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "Staff Dashboard",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            OutlinedButton(
                onClick = onLogout
            ) {
                Text("Logout")
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "Welcome, ${staff.name}",
            style =
                MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * WORKSHOP WIFI STATUS
         */
        Text(
            text = if (wifiConnected) {
                "Workshop Wi-Fi Connected"
            } else {
                "Workshop Wi-Fi Not Connected"
            },
            style =
                MaterialTheme.typography.bodyLarge
        )

        awaySince?.let { since ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Away from the workshop since ${WorkshopTime.display(since, attendance?.date)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = onRefreshWifi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Wi-Fi")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * ATTENDANCE
         */
        Text(
            text = "Today's Attendance",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        if (attendance?.checkInTime == null) {

            /*
             * CHECK IN
             */
            Button(
                onClick = {
                    if (wifiConnected) {
                        attendanceViewModel.checkIn(
                            staff.id,
                            onDone = onAttendanceChanged
                        )
                    }
                },
                enabled = wifiConnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (wifiConnected) {
                        "CHECK IN"
                    } else {
                        "CHECK IN — WORKSHOP WIFI REQUIRED"
                    }
                )
            }

        } else {

            /*
             * ALREADY CHECKED IN
             */
            Text(
                text =
                    "Checked In — ${
                        WorkshopTime.display(
                            attendance?.checkInTime,
                            attendance?.date
                        )
                    }",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (attendance?.checkOutTime == null) {

                /*
                 * CHECK OUT (Wi-Fi must still be connected)
                 */
                Button(
                    onClick = {
                        if (wifiConnected) {
                            attendanceViewModel.checkOut(
                                staff.id,
                                onDone = onAttendanceChanged
                            )
                        }
                    },
                    enabled = wifiConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (wifiConnected) {
                            "CHECK OUT"
                        } else {
                            "CHECK OUT — WORKSHOP WIFI REQUIRED"
                        }
                    )
                }

            } else {

                /*
                 * ALREADY CHECKED OUT
                 */
                Text(
                    text =
                        "Checked Out — ${
                            WorkshopTime.display(
                                attendance?.checkOutTime,
                                attendance?.date
                            )
                        }",
                    style =
                        MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * TOTAL WORKING HOURS
         */
        Text(
            text = "Total Working Hours",
            style =
                MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = formatMinutes(totalWorkingMinutes),
            style =
                MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * TIME SPENT IN WORKSHOP
         * (sum of all of today's workshop Wi-Fi sessions)
         */
        Text(
            text = "Time Spent in Workshop",
            style =
                MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = formatMinutes(workshopMinutes),
            style =
                MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        /*
         * WORK CHECKLIST (saved on the phone and in Firebase)
         */
        Text(
            text = "Today's Work Checklist",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        WORKSHOP_CHECKLIST.forEach { item ->
            ChecklistItem(
                title = item,
                checked = checklist[item] == true,
                onCheckedChange = { checked ->
                    checklist = checklist + (item to checked)
                    scope.launch {
                        checklistRepository.setItem(
                            staffId = staff.id,
                            staffName = staff.name,
                            date = checklistDate,
                            item = item,
                            checked = checked
                        )
                    }
                }
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

/* ============================================================
   CHECKLIST ITEM
   ============================================================ */

@Composable
fun ChecklistItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Text(
            text = title
        )
    }
}

fun formatMinutes(
    minutes: Long
): String = WorkshopTime.formatMinutes(minutes)
