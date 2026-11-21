package com.example.workshop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Staff::class,
        Admin::class,
        Attendance::class,
        WorkshopWifiSession::class,
        ChecklistItemEntry::class
    ],
    version = 4,
    exportSchema = false
)
abstract class WorkshopDatabase : RoomDatabase() {

    abstract fun staffDao(): StaffDao

    abstract fun adminDao(): AdminDao

    abstract fun attendanceDao(): AttendanceDao

    abstract fun workshopWifiSessionDao(): WorkshopWifiSessionDao

    abstract fun checklistDao(): ChecklistDao

    companion object {

        @Volatile
        private var INSTANCE: WorkshopDatabase? = null

        fun getDatabase(
            context: Context
        ): WorkshopDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        WorkshopDatabase::class.java,
                        "workshop_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()

                INSTANCE = instance

                /*
                 * Admin accounts now live in Firebase
                 * (see AdminRepository). No default admin
                 * is created in the local database.
                 */

                instance
            }
        }
    }
}