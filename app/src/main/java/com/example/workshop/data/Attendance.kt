package com.example.workshop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class Attendance(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val staffId: Int,

    val date: String,

    val checkInTime: String? = null,

    val checkOutTime: String? = null,

    val totalWorkingMinutes: Long = 0,

    val workshopMinutes: Long = 0
)