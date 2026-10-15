package com.example.workshop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface WorkshopWifiSessionDao {

    @Insert
    suspend fun insert(
        session: WorkshopWifiSession
    ): Long

    @Update
    suspend fun update(
        session: WorkshopWifiSession
    )

    @Query("""
        SELECT * FROM workshop_wifi_session
        WHERE staffId = :staffId
        AND date = :date
        AND disconnectedAt IS NULL
        LIMIT 1
    """)
    suspend fun getActiveSession(
        staffId: Int,
        date: String
    ): WorkshopWifiSession?

    @Query("""
        SELECT * FROM workshop_wifi_session
        WHERE staffId = :staffId
        AND date = :date
        ORDER BY connectedAt
    """)
    suspend fun getSessionsForDate(
        staffId: Int,
        date: String
    ): List<WorkshopWifiSession>
}