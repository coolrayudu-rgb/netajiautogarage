package com.example.workshop.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface StaffDao {

    @Insert
    suspend fun insert(staff: Staff)

    @Update
    suspend fun update(staff: Staff)

    @Delete
    suspend fun delete(staff: Staff)

    @Query("SELECT * FROM staff ORDER BY name")
    suspend fun getAllStaff(): List<Staff>

    @Query("""
    SELECT * FROM staff
    WHERE username = :username
    AND password = :password
    AND active = 1
    LIMIT 1
""")
    suspend fun login(username: String, password: String): Staff?

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun getStaffById(id: Int): Staff?

    @Query("DELETE FROM staff")
    suspend fun deleteAllStaff()
}