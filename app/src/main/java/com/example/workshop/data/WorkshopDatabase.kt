package com.example.workshop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Staff::class,
        Admin::class,
        Attendance::class,
        WorkshopWifiSession::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WorkshopDatabase : RoomDatabase() {

    abstract fun staffDao(): StaffDao

    abstract fun adminDao(): AdminDao

    abstract fun attendanceDao(): AttendanceDao

    abstract fun workshopWifiSessionDao(): WorkshopWifiSessionDao

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

                CoroutineScope(
                    Dispatchers.IO
                ).launch {

                    /*
                     * Create default Admin only
                     * if no Admin exists.
                     */
                    if (
                        instance.adminDao()
                            .getAdminCount() == 0
                    ) {

                        instance.adminDao().insert(
                            Admin(
                                username = "admin",
                                password = "admin123",
                                name = "Administrator",
                                active = true
                            )
                        )
                    }

                    /*
                     * Do NOT create a default Staff.
                     *
                     * Staff will be created by Admin.
                     */
                }

                instance
            }
        }
    }
}