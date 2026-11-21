package com.example.workshop

import androidx.lifecycle.ViewModel
import com.example.workshop.data.WorkshopWifiSession
import com.example.workshop.data.WorkshopWifiSessionRepository

/*
 * Read access to workshop Wi-Fi sessions for the screens.
 *
 * Starting and ending sessions is done by WorkshopTracker
 * (and by check-in / check-out in AttendanceViewModel).
 */
class WorkshopWifiSessionViewModel(
    private val repository: WorkshopWifiSessionRepository
) : ViewModel() {

    /*
     * All sessions of one shift. shiftDate is the date
     * of the shift's attendance record.
     */
    suspend fun getShiftSessions(
        staffId: Int,
        shiftDate: String
    ): List<WorkshopWifiSession> {

        return repository.getSessionsForDate(
            staffId = staffId,
            date = shiftDate
        )
    }
}
