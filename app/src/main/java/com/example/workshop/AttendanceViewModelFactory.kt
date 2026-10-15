package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workshop.data.AttendanceRepository
import com.example.workshop.data.WorkshopWifiSessionRepository

class AttendanceViewModelFactory(
    private val repository: AttendanceRepository,
    private val workshopWifiSessionRepository:
    WorkshopWifiSessionRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                AttendanceViewModel::class.java
            )
        ) {

            return AttendanceViewModel(
                repository,
                workshopWifiSessionRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}