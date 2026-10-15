package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workshop.data.WorkshopWifiSession
import com.example.workshop.data.WorkshopWifiSessionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkshopWifiSessionViewModel(
    private val repository: WorkshopWifiSessionRepository
) : ViewModel() {

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

    fun startSession(staffId: Int) {

        viewModelScope.launch {

            val date = today()

            val activeSession =
                repository.getActiveSession(
                    staffId = staffId,
                    date = date
                )

            /*
             * Don't create another session if
             * one is already active.
             */
            if (activeSession != null) {
                return@launch
            }

            repository.startSession(
                staffId = staffId,
                date = date,
                connectedAt = currentTime()
            )
        }
    }

    fun endSession(staffId: Int) {

        viewModelScope.launch {

            val date = today()

            val activeSession =
                repository.getActiveSession(
                    staffId = staffId,
                    date = date
                )
                    ?: return@launch

            repository.endSession(
                session = activeSession,
                disconnectedAt = currentTime()
            )
        }
    }

    suspend fun getTodaySessions(
        staffId: Int
    ): List<WorkshopWifiSession> {

        return repository.getSessionsForDate(
            staffId = staffId,
            date = today()
        )
    }
}