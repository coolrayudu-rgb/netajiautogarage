package com.example.workshop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AdminDao {

    @Insert
    suspend fun insert(admin: Admin)

    @Query("""
        SELECT * FROM admin
        WHERE username = :username
        AND password = :password
        AND active = 1
        LIMIT 1
    """)
    suspend fun login(
        username: String,
        password: String
    ): Admin?

    @Query("SELECT COUNT(*) FROM admin")
    suspend fun getAdminCount(): Int
}