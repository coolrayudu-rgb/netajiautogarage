package com.example.workshop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.workshop.data.AdminRepository
import com.google.firebase.FirebaseApp
import com.example.workshop.data.Staff
import com.example.workshop.data.StaffRepository
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.WorkshopWifiSessionRepository
import com.example.workshop.data.WorkshopDatabase
import kotlinx.coroutines.launch

private const val ALLOWED_WIFI = "Airtel_House_of_adapa"

class MainActivity : ComponentActivity() {

    private lateinit var database: WorkshopDatabase
    private lateinit var adminRepository: AdminRepository
    private lateinit var staffRepository: StaffRepository

    private lateinit var attendanceRepository: AttendanceRepository

    private lateinit var workshopWifiSessionRepository: WorkshopWifiSessionRepository

    private var wifiPermissionGranted = false

    /*
     * IMPORTANT:
     * Your AdminViewModelFactory accepts ONLY AdminRepository.
     */
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
            wifiPermissionGranted = checkWifiPermissions()
            refreshWifi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

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

        wifiPermissionGranted = checkWifiPermissions()

        if (!wifiPermissionGranted) {
            requestWifiPermissions()
        }

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

    private fun checkWifiPermissions(): Boolean {

        val locationPermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val nearbyWifiPermission =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED

            return locationPermission || nearbyWifiPermission
        }

        return locationPermission
    }

    private fun requestWifiPermissions() {

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }

        permissionLauncher.launch(
            permissions.toTypedArray()
        )
    }

    fun refreshWifi() {
        wifiPermissionGranted = checkWifiPermissions()
    }

    fun checkWorkshopWifi(): Boolean {

        android.util.Log.e(
            "WIFI_TEST",
            "========== WIFI CHECK START =========="
        )

        if (!wifiPermissionGranted) {

            android.util.Log.e(
                "WIFI_TEST",
                "Permission = FALSE"
            )

            return false
        }

        android.util.Log.e(
            "WIFI_TEST",
            "Permission = OK"
        )

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network =
            connectivityManager.activeNetwork

        if (network == null) {

            android.util.Log.e(
                "WIFI_TEST",
                "Active network = NULL"
            )

            return false
        }

        android.util.Log.e(
            "WIFI_TEST",
            "Active network = $network"
        )

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)

        if (capabilities == null) {

            android.util.Log.e(
                "WIFI_TEST",
                "Network capabilities = NULL"
            )

            return false
        }

        val isWifi =
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            )

        android.util.Log.e(
            "WIFI_TEST",
            "WiFi transport = $isWifi"
        )

        if (!isWifi) {
            return false
        }

        /*
         * First try NetworkCapabilities transportInfo.
         * This is more reliable on newer Android versions.
         */
        var currentSsid: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val wifiInfo =
                capabilities.transportInfo as? WifiInfo

            if (wifiInfo != null) {
                currentSsid = wifiInfo.ssid
            }
        }

        /*
         * Fallback to WifiManager.
         */
        if (
            currentSsid.isNullOrBlank() ||
            currentSsid == "<unknown ssid>"
        ) {

            val wifiManager =
                applicationContext.getSystemService(
                    Context.WIFI_SERVICE
                ) as WifiManager

            val wifiInfo =
                wifiManager.connectionInfo

            currentSsid = wifiInfo.ssid
        }

        currentSsid =
            currentSsid
                ?.removePrefix("\"")
                ?.removeSuffix("\"")

        android.util.Log.e(
            "WIFI_TEST",
            "SSID detected = [$currentSsid]"
        )

        android.util.Log.e(
            "WIFI_TEST",
            "SSID expected = [$ALLOWED_WIFI]"
        )

        val result =
            currentSsid == ALLOWED_WIFI

        android.util.Log.e(
            "WIFI_TEST",
            "FINAL RESULT = $result"
        )

        return result
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

    var wifiConnected by remember {
        mutableStateOf(activity.checkWorkshopWifi())
    }

    var loggedInStaff by remember {
        mutableStateOf<Staff?>(null)
    }

    var screen by remember {
        mutableStateOf("home")
    }

    /*
 * Check Wi-Fi when the application starts.
 */
    LaunchedEffect(Unit) {
        wifiConnected = activity.checkWorkshopWifi()
    }

    /*
     * Check Wi-Fi every 30 minutes while
     * the staff dashboard is open.
     */
    LaunchedEffect(screen) {

        if (screen == "staffHome") {

            while (true) {

                wifiConnected =
                    activity.checkWorkshopWifi()

                kotlinx.coroutines.delay(
                    30 * 60 * 1000L
                )
            }
        }
    }

    when (screen) {

        "home" -> {

            HomeScreen(
                wifiConnected = wifiConnected,

                onRefreshWifi = {
                    activity.refreshWifi()
                    wifiConnected =
                        activity.checkWorkshopWifi()
                },

                onAdminLogin = {
                    screen = "adminLogin"
                },

                onStaffLogin = {
                    screen = "staffLogin"
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
                    screen = "staffHome"
                }
            )
        }

        "staffHome" -> {

            loggedInStaff?.let { staff ->

                LaunchedEffect(
                    wifiConnected,
                    staff.id
                ) {
                    if (wifiConnected) {

                        workshopWifiSessionViewModel
                            .startSession(staff.id)

                    } else {

                        workshopWifiSessionViewModel
                            .endSession(staff.id)
                    }
                }

                StaffHomeScreen(
                    staff = staff,
                    attendanceViewModel = attendanceViewModel,
                    workshopWifiSessionViewModel =
                        workshopWifiSessionViewModel,
                    wifiConnected = wifiConnected,
                    onRefreshWifi = {
                        wifiConnected =
                            activity.checkWorkshopWifi()
                    },
                    onLogout = {
                        loggedInStaff = null
                        screen = "home"
                    }
                )
            }
        }

        "adminDashboard" -> {

            AdminDashboardScreen(
                staffRepository = staffRepository,

                onLogout = {
                    screen = "home"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

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
fun AdminDashboardScreen(
    staffRepository: StaffRepository,
    onLogout: () -> Unit
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
                text = "Admin Dashboard",
                style =
                    MaterialTheme.typography.headlineSmall
            )

            OutlinedButton(
                onClick = onLogout
            ) {
                Text("Logout")
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
   ADD StaffHomeScreen
   ============================================================ */

@Composable
fun StaffHomeScreen(
    staff: Staff,
    attendanceViewModel: AttendanceViewModel,
    workshopWifiSessionViewModel:
    WorkshopWifiSessionViewModel,
    wifiConnected: Boolean,
    onRefreshWifi: () -> Unit,
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

    LaunchedEffect(
        staff.id,
        wifiConnected,
        attendance?.checkInTime
    ) {

        while (true) {

            val now = Date()

            // -----------------------------
            // TOTAL WORKING HOURS
            // -----------------------------

            val checkIn =
                parseWorkshopTime(
                    attendance?.checkInTime
                )

            val checkOut =
                parseWorkshopTime(
                    attendance?.checkOutTime
                )

            val workingEnd =
                checkOut ?: now

            if (checkIn != null) {

                val minutes =
                    (
                            workingEnd.time -
                                    checkIn.time
                            ) / 60000L

                totalWorkingMinutes =
                    minutes.coerceAtLeast(0L)
            }


            // -----------------------------
            // TIME SPENT IN WORKSHOP
            // -----------------------------

            val sessions =
                workshopWifiSessionViewModel
                    .getTodaySessions(staff.id)

            workshopMinutes =
                sessions.sumOf { session ->

                    val start =
                        parseWorkshopTime(
                            session.connectedAt
                        )

                    val end =
                        parseWorkshopTime(
                            session.disconnectedAt
                        ) ?: if (
                            wifiConnected &&
                            start != null
                        ) {
                            now
                        } else {
                            null
                        }

                    if (
                        start != null &&
                        end != null
                    ) {

                        (
                                end.time -
                                        start.time
                                ) / 60000L

                    } else {
                        0L
                    }
                }


// -----------------------------
// UPDATE FIRESTORE LIVE
// -----------------------------

            val currentAttendance = attendance

            if (
                currentAttendance != null &&
                currentAttendance.checkInTime != null &&
                currentAttendance.checkOutTime == null
            ) {

                attendanceViewModel.updateLiveTimes(
                    attendance = currentAttendance,
                    totalWorkingMinutes =
                        totalWorkingMinutes,
                    workshopMinutes =
                        workshopMinutes
                )
            }


            kotlinx.coroutines.delay(
                60_000L
            )
        }
    }

    LaunchedEffect(staff.id) {
        attendanceViewModel.loadTodayAttendance(staff.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        /*
         * CHECK IN
         */
        if (attendance?.checkInTime == null) {

            Button(
                onClick = {

                    /*
                     * Wi-Fi is checked again immediately
                     * before allowing Check In.
                     */
                    if (wifiConnected) {

                        attendanceViewModel.checkIn(
                            staff.id
                        )

                        workshopWifiSessionViewModel.startSession(
                            staff.id
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
                    "Checked In — ${attendance?.checkInTime}",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            /*
             * CHECK OUT
             */
            if (attendance?.checkOutTime == null) {

                Button(
                    onClick = {

                        /*
                         * Wi-Fi must still be connected
                         * to check out.
                         */
                        if (wifiConnected) {

                            attendanceViewModel.checkOut(
                                staff.id
                            )

                            workshopWifiSessionViewModel.endSession(
                                staff.id
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
                        "Checked Out — ${attendance?.checkOutTime}",
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
            text = formatMinutes(
                totalWorkingMinutes
            ),
            style =
                MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * TIME SPENT IN WORKSHOP
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
         * WORK CHECKLIST
         */
        Text(
            text = "Today's Work Checklist",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        ChecklistItem("Shed Opened")
        ChecklistItem("Cleaning")
        ChecklistItem("Sorted Bikes")
        ChecklistItem("Sorted Wrenches")
        ChecklistItem("Engine Assembled")
        ChecklistItem("Bore Assembled")
        ChecklistItem("Engine Disassembled")
    }
}
/* ============================================================
   ADD ChecklistItem
   ============================================================ */
@Composable
fun ChecklistItem(
    title: String
) {
    var checked by remember {
        mutableStateOf(false)
    }

    androidx.compose.foundation.layout.Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
            }
        )

        Text(
            text = title
        )
    }
}

private fun parseWorkshopTime(
    time: String?
): Date? {

    if (time == null) {
        return null
    }

    return try {

        val timeFormat =
            SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            )

        val parsedTime =
            timeFormat.parse(time)
                ?: return null

        val calendar =
            java.util.Calendar.getInstance()

        val timeCalendar =
            java.util.Calendar.getInstance()

        timeCalendar.time = parsedTime

        calendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            timeCalendar.get(
                java.util.Calendar.HOUR_OF_DAY
            )
        )

        calendar.set(
            java.util.Calendar.MINUTE,
            timeCalendar.get(
                java.util.Calendar.MINUTE
            )
        )

        calendar.set(
            java.util.Calendar.SECOND,
            0
        )

        calendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        calendar.time

    } catch (e: Exception) {
        null
    }
}
fun formatMinutes(
    minutes: Long
): String {

    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return "${hours}h ${remainingMinutes}m"
}

