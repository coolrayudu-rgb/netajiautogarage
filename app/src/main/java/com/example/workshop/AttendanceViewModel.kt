package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workshop.data.Attendance
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.WorkshopTime
import com.example.workshop.data.WorkshopWifiSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * Check-in / check-out for the staff member on this phone.
 *
 * "Today's attendance" is the current shift: today's
 * record, or yesterday's if that shift is still open
 * past midnight. All times are saved with their date
 * (see WorkshopTime).
 */
class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val workshopWifiSessionRepository:
    WorkshopWifiSessionRepository
) : ViewModel() {

    private val _todayAttendance =
        MutableStateFlow<Attendance?>(null)

    val todayAttendance: StateFlow<Attendance?> =
        _todayAttendance.asStateFlow()

    fun loadTodayAttendance(staffId: Int) {

        viewModelScope.launch {
            _todayAttendance.value =
                repository.getCurrentAttendance(staffId)
        }
    }

    fun clear() {
        _todayAttendance.value = null
    }

    fun checkIn(staffId: Int, onDone: () -> Unit = {}) {

        viewModelScope.launch {

            val existing =
                repository.getCurrentAttendance(staffId)

            // One check-in per shift.
            if (existing != null) {
                _todayAttendance.value = existing
                onDone()
                return@launch
            }

            val date = WorkshopTime.today()
            val now = WorkshopTime.now()

            repository.checkIn(
                staffId = staffId,
                date = date,
                time = now
            )

            // The staff member is on the workshop Wi-Fi
            // (check-in needs it), so the first workshop
            // session starts now.
            if (workshopWifiSessionRepository.getActiveSession(staffId, date) == null) {
                workshopWifiSessionRepository.startSession(
                    staffId = staffId,
                    date = date,
                    connectedAt = now
                )
            }

            _todayAttendance.value =
                repository.getCurrentAttendance(staffId)

            onDone()
        }
    }

    fun checkOut(staffId: Int, onDone: () -> Unit = {}) {

        viewModelScope.launch {

            val attendance =
                repository.getCurrentAttendance(staffId)
                    ?: return@launch

            // Don't allow another checkout.
            if (attendance.checkOutTime != null) {
                _todayAttendance.value = attendance
                onDone()
                return@launch
            }

            val shiftDate = attendance.date
            val now = WorkshopTime.now()

            // End the open workshop Wi-Fi session first.
            workshopWifiSessionRepository
                .getActiveSession(staffId, shiftDate)
                ?.let { active ->
                    workshopWifiSessionRepository.endSession(
                        session = active,
                        disconnectedAt = now
                    )
                }

            // Total working time: check-in → now (dates included).
            val totalMinutes =
                WorkshopTime.minutesBetween(
                    attendance.checkInTime,
                    now,
                    shiftDate
                )

            // Workshop time: sum of all the shift's sessions.
            val sessions =
                workshopWifiSessionRepository.getSessionsForDate(
                    staffId = staffId,
                    date = shiftDate
                )

            val workshopMinutes =
                WorkshopTracker.workshopMinutes(sessions, shiftDate)

            repository.checkOut(
                attendance = attendance,
                time = now,
                totalWorkingMinutes = totalMinutes,
                workshopMinutes = workshopMinutes
            )

            _todayAttendance.value =
                repository.getCurrentAttendance(staffId)

            onDone()
        }
    }
}
