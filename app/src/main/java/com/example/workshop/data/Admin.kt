package com.example.workshop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin")
data class Admin(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val username: String,

    val password: String,

    val name: String,

    val active: Boolean = true
)