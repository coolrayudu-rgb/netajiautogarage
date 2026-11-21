# Nethaji Auto Garage – App Purpose

| | |
|---|---|
| **Document version** | 1.0 |
| **App version** | 1.0 (versionCode 1) |
| **Date** | 21 November 2026 |
| **Platform** | Android (min SDK 29 / Android 10, target SDK 36) |
| **Package** | `com.example.workshop` |

---

## 1. Purpose

Nethaji Auto Garage is an Android app for running the day-to-day staff side of a two-wheeler / auto repair workshop. Its main job is to record **when staff are actually at the workshop**.

Staff can only check in and check out while their phone is connected to the workshop's own Wi-Fi network. While they are checked in, the app keeps tracking how long they stay connected to that Wi-Fi, so the owner can see two numbers for every person, every day:

- **Total working hours**: from check-in to check-out.
- **Time spent in the workshop**: the total time the phone was actually on the workshop Wi-Fi.

The gap between the two shows how much of a shift was spent away from the garage. The owner (admin) sees all of this live on a dashboard, and also manages the staff list from the app.

## 2. Who it is for

| User | What they do in the app |
|---|---|
| **Admin (garage owner / manager)** | Logs in to the admin dashboard, watches today's attendance for all staff in near real time, and adds, edits, deactivates or deletes staff accounts. |
| **Staff (mechanics and workers)** | Log in with the username and password the admin gave them, check in and check out on the workshop Wi-Fi, see their own working hours and workshop time, and go through the daily workshop checklist. |

## 3. Main features

### 3.1 Home screen
- Entry point with **Admin Login** and **Staff Login**.
- Shows whether the phone is currently on the workshop Wi-Fi, with a **Refresh Wi-Fi Status** button.
- A **Test Notification** button that checks notification permission and shows a sample notification.

### 3.2 Workshop Wi-Fi verification
- The app reads the name (SSID) of the connected Wi-Fi network and compares it to the one workshop network set in the code (`ALLOWED_WIFI` in `MainActivity.kt`).
- It needs location / nearby Wi-Fi permission, because Android hides the Wi-Fi name without it. The app asks for this when it starts.
- The Wi-Fi is checked when the app opens, when the user taps refresh, and every 30 minutes while the staff dashboard is open.

### 3.3 Staff login
- Staff log in with a username and password.
- Only staff marked **active** can log in.
- Login is checked against the cloud (Firebase Firestore) first and falls back to the copy stored on the phone if there is no internet.

### 3.4 Staff attendance (check in / check out)
- **Check In** and **Check Out** are only enabled while on the workshop Wi-Fi. Off the network the buttons read "WORKSHOP WIFI REQUIRED".
- One attendance record per staff member per day. A second check-in or check-out on the same day is blocked.
- At check-out the app works out total working minutes and total workshop Wi-Fi minutes and saves them.
- While a staff member is checked in, their running totals are recalculated and uploaded every minute, so the admin dashboard stays current.

### 3.5 Workshop time tracking (Wi-Fi sessions)
- After check-in, each period connected to the workshop Wi-Fi is saved as a **session** (connected at / disconnected at).
- Leaving the Wi-Fi ends the current session; coming back starts a new one.
- "Time spent in workshop" is the sum of all of that day's sessions.

### 3.6 Staff dashboard
- Greets the logged-in staff member and shows the Wi-Fi status.
- Check In / Check Out buttons with the recorded times.
- Live **Total Working Hours** and **Time Spent in Workshop**.
- A **daily workshop checklist**: Shed Opened, Cleaning, Sorted Bikes, Sorted Wrenches, Engine Assembled, Bore Assembled, Engine Disassembled. In version 1 the ticks are not saved.

### 3.7 Admin login
- Admins log in with a username and password stored on the device.
- If no admin exists, a default admin account is created on first launch. **Change its password before real use** (see section 6).

### 3.8 Admin dashboard
- Staff totals: total, active and inactive.
- Today's attendance summary: how many are checked in, working, and checked out.
- One row per staff member with status (Not checked in, Currently working, Checked out), check-in time, check-out time, total working time and workshop time.
- Refreshes automatically every 5 seconds.
- A button opens Staff Management.

### 3.9 Staff management (admin)
- List all staff with Edit and Delete.
- **Add Staff**: name, username, password. The app gives each new staff member the next free number as their ID.
- **Edit Staff**: change details or mark the person active or inactive.
- **Refresh Staff** pulls the latest list from the cloud.

### 3.10 Notifications
- Creates a "Garage Notifications" channel and registers the phone with Firebase Cloud Messaging so it can receive push messages. Version 1 only includes the test notification. No real alerts are sent yet.

## 4. How data is stored

The app works **cloud first with an offline fallback**:

- **Firebase Firestore (cloud)** is the main shared store, so staff phones and the admin phone see the same data.
- **Room (SQLite on the phone)** keeps a local copy and is used when the cloud can't be reached.

| Data | Cloud (Firestore collection) | Local (Room table) | Key fields |
|---|---|---|---|
| Staff | `staff` (document id = staff id) | `staff` | id, name, username, password, active |
| Attendance | `attendance` (document id = `staffId_date`) | `attendance` | staffId, date, checkInTime, checkOutTime, totalWorkingMinutes, workshopMinutes |
| Workshop Wi-Fi sessions | `workshopWifiSessions` | `workshop_wifi_session` | staffId, date, connectedAt, disconnectedAt |
| Admin | (local only) | `admin` | username, password, name, active |

Dates are saved as `yyyy-MM-dd` and times as `hh:mm a` (for example `09:15 AM`).

## 5. Technology

- **Language / UI:** Kotlin, Jetpack Compose, Material 3
- **Architecture:** ViewModels and repositories over Room DAOs and Firestore
- **Local database:** Room (database version 3)
- **Cloud:** Firebase Firestore, Firebase Cloud Messaging
- **Build:** Gradle Kotlin DSL, Java 17
- **Android permissions:** Wi-Fi state, network state, fine and coarse location, nearby Wi-Fi devices (Android 13+), notifications, internet

## 6. Known limits in version 1

- **One workshop Wi-Fi name, set in code.** Changing the network means changing `ALLOWED_WIFI` and rebuilding the app.
- **Passwords are stored as plain text** in Firestore and on the phone, and a default admin account is created automatically. Both should be fixed before wider use.
- **Admin accounts are per device.** They live only in local storage and are not synced.
- **Time maths uses only the clock time** (`hh:mm a`, no date). A shift that runs past midnight will be calculated wrongly.
- **Workshop time updates only while the app is open.** The Wi-Fi checks and the per-minute updates run in the foreground. There is no background service yet.
- **The checklist is not saved**, and push notifications are set up but not used yet.
- **Database upgrades wipe local data** (`fallbackToDestructiveMigration`). Cloud data is not affected.

## 7. Version history

| Version | Date | Notes |
|---|---|---|
| 1.0 | 21 Nov 2026 | First write-up of the app's purpose, features and data model, based on the current code. |
