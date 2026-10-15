package com.example.workshop.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff")
data class Staff(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    val username: String,

    val password: String,

    val active: Boolean = true
)