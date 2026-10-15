package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workshop.data.Attendance
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.WorkshopWifiSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val workshopWifiSessionRepository:
    WorkshopWifiSessionRepository
) : ViewModel() {

    private val _todayAttendance =
        MutableStateFlow<Attendance?>(null)

    val todayAttendance: StateFlow<Attendance?> =
        _todayAttendance.asStateFlow()

    private val dateFormat =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        )

    private val timeFormat =
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        )

    private fun today(): String {
        return dateFormat.format(Date())
    }

    private fun currentTime(): String {
        return timeFormat.format(Date())
    }

    fun loadTodayAttendance(staffId: Int) {

        viewModelScope.launch {

            _todayAttendance.value =
                repository.getTodayAttendance(
                    staffId = staffId,
                    date = today()
                )
        }
    }

    fun checkIn(staffId: Int) {

        viewModelScope.launch {

            val existing =
                repository.getTodayAttendance(
                    staffId = staffId,
                    date = today()
                )

            if (existing != null) {

                _todayAttendance.value = existing

                return@launch
            }

            repository.checkIn(
                staffId = staffId,
                date = today(),
                time = currentTime()
            )

            _todayAttendance.value =
                repository.getTodayAttendance(
                    staffId = staffId,
                    date = today()
                )
        }
    }

    fun checkOut(staffId: Int) {

        viewModelScope.launch {

            val attendance =
                repository.getTodayAttendance(
                    staffId = staffId,
                    date = today()
                )
                    ?: return@launch

            /*
             * Don't allow another checkout.
             */
            if (attendance.checkOutTime != null) {

                _todayAttendance.value =
                    attendance

                return@launch
            }

            // --------------------------------
            // TOTAL WORKING MINUTES
            // --------------------------------

            val checkInTime =
                parseTime(
                    attendance.checkInTime
                )

            val checkOutTime =
                parseTime(
                    currentTime()
                )

            val totalMinutes =
                if (
                    checkInTime != null &&
                    checkOutTime != null
                ) {

                    (
                            checkOutTime.time -
                                    checkInTime.time
                            ) / 60000L

                } else {
                    0L
                }

            // --------------------------------
            // WORKSHOP WIFI MINUTES
            // --------------------------------

            // End any active workshop Wi-Fi session first
            val activeSession =
                workshopWifiSessionRepository.getActiveSession(
                    staffId = staffId,
                    date = today()
                )

            if (activeSession != null) {
                workshopWifiSessionRepository.endSession(
                    session = activeSession,
                    disconnectedAt = currentTime()
                )
            }

// Now get all today's sessions
            val sessions =
                workshopWifiSessionRepository
                    .getSessionsForDate(
                        staffId = staffId,
                        date = today()
                    )

            val workshopMinutes =
                sessions.sumOf { session ->

                    val start =
                        parseTime(
                            session.connectedAt
                        )

                    val end =
                        parseTime(
                            session.disconnectedAt
                        )

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

            // --------------------------------
            // SAVE CHECKOUT
            // --------------------------------

            repository.checkOut(
                attendance = attendance,
                time = currentTime(),
                totalWorkingMinutes =
                    totalMinutes,
                workshopMinutes =
                    workshopMinutes
            )

            _todayAttendance.value =
                repository.getTodayAttendance(
                    staffId = staffId,
                    date = today()
                )
        }
    }

    suspend fun updateLiveTimes(
        attendance: Attendance,
        totalWorkingMinutes: Long,
        workshopMinutes: Long
    ) {
        repository.updateLiveTimes(
            attendance = attendance,
            totalWorkingMinutes =
                totalWorkingMinutes,
            workshopMinutes =
                workshopMinutes
        )
    }

    private fun parseTime(
        time: String?
    ): Date? {

        if (time == null) {
            return null
        }

        return try {

            timeFormat.parse(time)

        } catch (
            e: Exception
        ) {

            null
        }
    }
}