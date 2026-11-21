# Nethaji Auto Garage – App Purpose

| | |
|---|---|
| **Document version** | 1.1 |
| **App version** | 1.0 (versionCode 1) |
| **Date** | 21 November 2026 |
| **Platform** | Android (min SDK 29 / Android 10, target SDK 36) |
| **Package** | `com.example.workshop` |

---

## 1. Purpose

Nethaji Auto Garage is an Android app for running the day-to-day staff side of a two-wheeler / auto repair workshop. Its main job is to record **when staff are actually at the workshop**.

Staff can only check in and check out while their phone is connected to the workshop's Wi-Fi network. While they are checked in, the app keeps tracking, in the background too, how long they stay connected to that Wi-Fi. It warns the staff member, the admin and everyone else logged in when someone stays away too long.

For every person, every day, the owner sees:

- **Total working hours**: from check-in to check-out.
- **Time spent in the workshop**: the sum of all of that day's Wi-Fi sessions.
- **Away status**: whether a checked-in person is off the workshop Wi-Fi right now, and since when.
- **Checklist progress**: how many of the daily workshop tasks are ticked.

## 2. Who it is for

| User | What they do in the app |
|---|---|
| **Admin (garage owner / manager)** | Logs in to the admin dashboard, watches today's attendance for all staff in near real time, gets alerts when staff are away, sets the workshop Wi-Fi name, and adds, edits, deactivates or deletes staff accounts. |
| **Staff (mechanics and workers)** | Log in with the username and password the admin gave them, check in and check out on the workshop Wi-Fi, see their own working hours and workshop time, tick off the daily workshop checklist, and get a reminder when they have been away for 15 minutes. |

## 3. Main features

### 3.1 Home screen
- Entry point with **Admin Login** and **Staff Login**.
- Shows whether the phone is on the workshop Wi-Fi, with a **Refresh Wi-Fi Status** button.
- The screen scrolls on small phones.
- *Changed in 1.1:* the Test Notification button is gone. The app asks for notification permission when it opens.

### 3.2 Workshop Wi-Fi verification
- The app reads the name (SSID) of the connected Wi-Fi and compares it to the **workshop Wi-Fi name set by the admin** (section 3.11).
- It needs location / nearby Wi-Fi permission, because Android hides the Wi-Fi name without it. It asks for fine and coarse location together, since Android 12 and later ignores a request for fine location alone.
- The Wi-Fi is checked when the app opens, when the user taps refresh, after logging in, checking in or checking out, and **every 5 minutes**, including in the background. *Changed in 1.1:* it used to be every 30 minutes, only while the staff dashboard was open.

### 3.3 Staff login
- Staff log in with a username and password. Only **active** staff can log in.
- Login is checked against Firebase first, and against the copy on the phone if there is no internet.
- *New in 1.1:* the login is remembered, so tracking keeps running in the background and the dashboard reopens straight away. Logging out stops tracking for that person.

### 3.4 Staff attendance (check in / check out)
- **Check In** and **Check Out** only work on the workshop Wi-Fi. Off the network the buttons read "WORKSHOP WIFI REQUIRED".
- One attendance record per shift. The first workshop Wi-Fi session starts at check-in.
- At check-out the app closes the open Wi-Fi session and saves total working minutes and workshop minutes.
- While checked in, the running totals are uploaded every minute, in the background too, so the admin dashboard stays current.
- *New in 1.1:* **shifts that run past midnight work.** A shift stays open until check-out, even after the date changes.

### 3.5 Time spent in the workshop (Wi-Fi sessions)
- After check-in, each period on the workshop Wi-Fi is saved as a **session** with a connected time and a disconnected time.
- Leaving the Wi-Fi ends the current session. Coming back starts a new one.
- **Time spent in the workshop = the sum of all of the day's sessions.** A session that is still open counts up to now. Staff dashboard, admin dashboard and check-out all use the same calculation.

### 3.6 Staff dashboard
- Greets the logged-in staff member and shows the Wi-Fi status. If the person is away, it shows since when.
- Check In / Check Out with the recorded date and time.
- Live **Total Working Hours** and **Time Spent in Workshop**, refreshed every minute.
- The **daily workshop checklist** (section 3.12).
- **The whole page scrolls**, so everything can be reached on small screens.

### 3.7 Admin login
- Admins log in with a username and password **stored in Firebase** (section 4).
- If Firebase has no admin yet, the first login attempt creates one: the admin saved on the phone by version 1, or else the default `admin` / `admin123`. **Change that password in the Firebase console before real use.**
- Each admin who logs in is also saved on the phone, so login works offline.

### 3.8 Admin dashboard
- Staff totals: total, active and inactive.
- Today's summary: checked in, working, **away** and checked out.
- One row per staff member with status (Not checked in, Currently working, **Away from workshop for Xh Ym**, Checked out), check-in and check-out date and time, total working time, time in workshop, and **checklist progress** (for example "3 of 7 done").
- Shifts still open from yesterday are included.
- Refreshes every 5 seconds while open.
- Includes the **Workshop Wi-Fi** setting (section 3.11) and a button to Staff Management.
- The page scrolls.

### 3.9 Staff management (admin)
- List all staff with Edit and Delete.
- **Add Staff**: name, username, password. Each new staff member gets the next free number as their ID.
- **Edit Staff**: change details or mark the person active or inactive.
- **Refresh Staff** pulls the latest list from Firebase.

### 3.10 Away-from-workshop notifications
"Away" means checked in, not checked out, and off the workshop Wi-Fi. The time away is counted from the end of the last Wi-Fi session.

| Time away | Who is notified | How |
|---|---|---|
| **15 minutes** | The staff member who is away | Notification on their own phone |
| **30 minutes** | The admin | Alert shared through Firebase, shown on every phone where the admin is logged in |
| **60 minutes** | The admin and **every logged-in staff member** | Alert shared through Firebase, shown on all logged-in phones |

- Away time is detected by the staff member's phone and by any phone with the admin logged in. If one phone misses it, the other catches it.
- Each alert is sent once per time away. Coming back to the Wi-Fi and leaving again starts a new count.
- Alerts use the "Workshop alerts" channel, which is high priority.

### 3.11 Workshop Wi-Fi setting (admin) — new in 1.1
- On the admin dashboard, the admin can type a new required Wi-Fi name and save it. **Use this phone's current Wi-Fi** fills in the name of the network the admin's phone is on.
- The name is saved in Firebase, so every phone picks it up on its next Wi-Fi check, within 5 minutes. Each phone keeps the last known name so the check still works offline.
- Until the admin sets a name, the app uses the original network, `TP-Link_0E14`.

### 3.12 Daily workshop checklist — new in 1.1
- Items: Shed Opened, Cleaning, Sorted Bikes, Sorted Wrenches, Engine Assembled, Bore Assembled, Engine Disassembled.
- Each tick is **saved on the phone straight away and pushed to Firebase**. If the phone is offline, it is sent when the phone reconnects.
- Ticks are kept per staff member per shift. They are still there after closing the app, and the admin sees the progress.

### 3.13 Background tracking — new in 1.1
- While an admin or staff member is logged in, the app runs a **background service** that keeps going with the app closed or the screen off. Android shows a small "Tracking workshop time" notification while it runs.
- Every minute it uploads live times and checks for away alerts. Every 5 minutes it checks the Wi-Fi. It listens for alerts all the time.
- The service stops when everyone on that phone has logged out.
- If background tracking can't start, for example because location permission was refused, the same tracking runs while the app is open.

## 4. How data is stored

The app works **cloud first with an offline fallback**:

- **Firebase Firestore (cloud)** is the main shared store, so all phones see the same data.
- **Room (SQLite on the phone)** keeps a local copy for when the cloud can't be reached.

| Data | Firestore collection | Room table | Key fields |
|---|---|---|---|
| Staff | `staff` (id = staff id) | `staff` | id, name, username, password, active |
| **Admins** *(new in 1.1)* | `admins` (id = username) | `admin` | username, password, name, active |
| Attendance | `attendance` (id = `staffId_date`) | `attendance` | staffId, date, checkInTime, checkOutTime, totalWorkingMinutes, workshopMinutes |
| Workshop Wi-Fi sessions | `workshopWifiSessions` | `workshop_wifi_session` | staffId, date, connectedAt, disconnectedAt |
| **Checklist** *(new in 1.1)* | `checklists` (id = `staffId_date`) | `checklist_item` | staffId, staffName, date, items (item → ticked), updatedAt |
| **Away alerts** *(new in 1.1)* | `awayAlerts` | — | staffId, staffName, date, awaySince, level (30 or 60), createdAtMillis |
| **Settings** *(new in 1.1)* | `settings/workshop` | (cached on the phone) | wifiName |

**Dates and times** *(changed in 1.1)*:
- Every time is saved **with its date**, for example `2026-11-21 09:15 AM`, always with English AM/PM so every phone can read it.
- `date` (`yyyy-MM-dd`) is the day the shift started. A shift's Wi-Fi sessions and checklist use that date even after midnight.
- Records saved by version 1.0 (time only, such as `09:15 AM`) are still read, using the record's own date.
- On screen, times show as "21 Nov, 09:15 AM".
- The local database moved to version 4. The update clears the phone's local copy once, and it refills from Firebase.

## 5. Technology

- **Language / UI:** Kotlin, Jetpack Compose, Material 3
- **Architecture:** ViewModels and repositories over Room DAOs and Firestore. A shared `WorkshopTracker` holds the tracking logic, used by both the screens and `WorkshopTrackingService`.
- **Local database:** Room (database version 4)
- **Cloud:** Firebase Firestore, Firebase Cloud Messaging
- **Build:** Gradle Kotlin DSL, Java 17
- **Android permissions:** Wi-Fi state, network state, fine and coarse location, nearby Wi-Fi devices, notifications, internet, foreground service (location type)

## 6. Known limits in version 1.1

- **Passwords are stored as plain text** in Firestore and on the phone, and a default admin is created in Firebase if none exists. Password hashing or Firebase Authentication should replace this before wider use.
- **Firestore security rules** must allow the new `admins`, `checklists`, `awayAlerts` and `settings` collections.
- **Tracking depends on each staff phone.** If a staff member's phone is switched off or the app is force-stopped while they are on the Wi-Fi, their session stays open and they don't show as away.
- **Away detection can lag by up to 5 minutes**, because the Wi-Fi is checked every 5 minutes.
- **Battery savers** on some phones (for example Xiaomi, Oppo, Vivo, Samsung) can still pause background apps. On those phones, set the app's battery use to "Unrestricted".
- **Alerts reach only phones where someone is logged in**, because they are delivered through the app's own background service and not through a push server.
- **Logging out while checked in stops tracking** for that person until they log in again.

## 7. Version history

| Version | Date | Notes |
|---|---|---|
| 1.0 | 21 Nov 2026 | First write-up of the app's purpose, features and data model. |
| 1.1 | 21 Nov 2026 | Removed the test notification. Wi-Fi check every 5 minutes. Background tracking service. Away notifications at 15, 30 and 60 minutes. Admin can set the workshop Wi-Fi name. Admin accounts in Firebase. Checklist saved on the phone and in Firebase. Date stored with every time, so shifts can run past midnight. Scrollable screens. Admin dashboard shows away status, total working time and checklist progress. |
