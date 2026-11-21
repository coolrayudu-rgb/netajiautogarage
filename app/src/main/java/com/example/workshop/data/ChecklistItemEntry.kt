package com.example.workshop.data

import androidx.room.Entity

/*
 * One ticked (or unticked) checklist item for one
 * staff member on one day.
 */
@Entity(
    tableName = "checklist_item",
    primaryKeys = ["staffId", "date", "item"]
)
data class ChecklistItemEntry(

    val staffId: Int,

    val date: String,

    val item: String,

    val checked: Boolean,

    // Date and time of the last change, "2026-11-21 09:15 AM"
    val updatedAt: String
)
