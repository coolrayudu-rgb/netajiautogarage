package com.example.workshop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workshop_wifi_session")
data class WorkshopWifiSession(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val staffId: Int,

    val date: String,

    val connectedAt: String,

    val disconnectedAt: String? = null
)