package com.example.workshop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insert(
        attendance: Attendance
    ): Long

    @Update
    suspend fun update(
        attendance: Attendance
    )

    @Query("""
        SELECT * FROM attendance
        WHERE staffId = :staffId
        AND date = :date
        LIMIT 1
    """)
    suspend fun getAttendance(
        staffId: Int,
        date: String
    ): Attendance?

    @Query("""
        SELECT * FROM attendance
        WHERE staffId = :staffId
        ORDER BY date DESC
    """)
    suspend fun getStaffAttendance(
        staffId: Int
    ): List<Attendance>
}