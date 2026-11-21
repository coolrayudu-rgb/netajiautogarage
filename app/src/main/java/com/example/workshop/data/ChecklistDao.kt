package com.example.workshop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChecklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ChecklistItemEntry)

    @Query("""
        SELECT * FROM checklist_item
        WHERE staffId = :staffId
        AND date = :date
    """)
    suspend fun getChecklist(
        staffId: Int,
        date: String
    ): List<ChecklistItemEntry>
}
