package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workshop.data.WorkshopWifiSessionRepository

class WorkshopWifiSessionViewModelFactory(
    private val repository: WorkshopWifiSessionRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                WorkshopWifiSessionViewModel::class.java
            )
        ) {

            return WorkshopWifiSessionViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}