package com.example.workshop.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AttendanceRepository(
    private val attendanceDao: AttendanceDao
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private fun documentId(
        staffId: Int,
        date: String
    ): String {
        return "${staffId}_${date}"
    }

    suspend fun getTodayAttendance(
        staffId: Int,
        date: String
    ): Attendance? {

        return try {

            val snapshot =
                firestore
                    .collection("attendance")
                    .document(
                        documentId(
                            staffId,
                            date
                        )
                    )
                    .get()
                    .await()

            if (snapshot.exists()) {

                val attendance =
                    Attendance(
                        id = 0,
                        staffId =
                            snapshot.getLong("staffId")
                                ?.toInt()
                                ?: staffId,
                        date =
                            snapshot.getString("date")
                                ?: date,
                        checkInTime =
                            snapshot.getString(
                                "checkInTime"
                            ),
                        checkOutTime =
                            snapshot.getString(
                                "checkOutTime"
                            ),
                        totalWorkingMinutes =
                            snapshot.getLong(
                                "totalWorkingMinutes"
                            ) ?: 0L,
                        workshopMinutes =
                            snapshot.getLong(
                                "workshopMinutes"
                            ) ?: 0L
                    )

                // Update local Room cache
                val existing =
                    attendanceDao.getAttendance(
                        staffId = staffId,
                        date = date
                    )

                if (existing == null) {

                    attendanceDao.insert(
                        attendance
                    )

                } else {

                    attendanceDao.update(
                        attendance.copy(
                            id = existing.id
                        )
                    )
                }

                attendance

            } else {

                attendanceDao.getAttendance(
                    staffId = staffId,
                    date = date
                )
            }

        } catch (e: Exception) {

            // Firebase unavailable → use Room
            attendanceDao.getAttendance(
                staffId = staffId,
                date = date
            )
        }
    }

    suspend fun getAllTodayAttendance(
        date: String
    ): List<Attendance> {

        return try {
            val snapshot = firestore
                .collection("attendance")
                .whereEqualTo("date", date)
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->
                val staffId = document.getLong("staffId")?.toInt()
                    ?: return@mapNotNull null

                Attendance(
                    id = 0,
                    staffId = staffId,
                    date = document.getString("date") ?: date,
                    checkInTime = document.getString("checkInTime"),
                    checkOutTime = document.getString("checkOutTime"),
                    totalWorkingMinutes =
                        document.getLong("totalWorkingMinutes") ?: 0L,
                    workshopMinutes =
                        document.getLong("workshopMinutes") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }


    suspend fun checkIn(
        staffId: Int,
        date: String,
        time: String
    ): Long {

        val attendance = Attendance(
            staffId = staffId,
            date = date,
            checkInTime = time,
            checkOutTime = null,
            totalWorkingMinutes = 0,
            workshopMinutes = 0
        )

        // Save locally
        val localId =
            attendanceDao.insert(attendance)

        println("🔥 FIRESTORE ATTENDANCE CHECK-IN STARTED")

        // Save to Firestore
        val data =
            hashMapOf(
                "staffId" to staffId,
                "date" to date,
                "checkInTime" to time,
                "checkOutTime" to null,
                "totalWorkingMinutes" to 0,
                "workshopMinutes" to 0
            )

        firestore
            .collection("attendance")
            .document(
                documentId(
                    staffId,
                    date
                )
            )
            .set(data)
            .await()

        return localId
    }

    suspend fun checkOut(
        attendance: Attendance,
        time: String,
        totalWorkingMinutes: Long,
        workshopMinutes: Long
    ) {

        // Update locally
        attendanceDao.update(
            attendance.copy(
                checkOutTime = time,
                totalWorkingMinutes = totalWorkingMinutes,
                workshopMinutes = workshopMinutes
            )
        )

        // Update Firestore
        val data =
            hashMapOf(
                "staffId" to attendance.staffId,
                "date" to attendance.date,
                "checkInTime" to attendance.checkInTime,
                "checkOutTime" to time,
                "totalWorkingMinutes" to totalWorkingMinutes,
                "workshopMinutes" to workshopMinutes
            )

        firestore
            .collection("attendance")
            .document(
                documentId(
                    attendance.staffId,
                    attendance.date
                )
            )
            .set(data)
            .await()
    }

    suspend fun updateLiveTimes(
        attendance: Attendance,
        totalWorkingMinutes: Long,
        workshopMinutes: Long
    ) {

        // Update local Room database
        attendanceDao.update(
            attendance.copy(
                totalWorkingMinutes = totalWorkingMinutes,
                workshopMinutes = workshopMinutes
            )
        )

        // Update Firestore
        val data =
            hashMapOf(
                "staffId" to attendance.staffId,
                "date" to attendance.date,
                "checkInTime" to attendance.checkInTime,
                "checkOutTime" to attendance.checkOutTime,
                "totalWorkingMinutes" to totalWorkingMinutes,
                "workshopMinutes" to workshopMinutes
            )

        firestore
            .collection("attendance")
            .document(
                documentId(
                    attendance.staffId,
                    attendance.date
                )
            )
            .set(data)
            .await()
    }

    private suspend fun <T>
            com.google.android.gms.tasks.Task<T>.await(): T {

        return suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            addOnFailureListener { exception ->

                if (continuation.isActive) {
                    continuation.resumeWithException(
                        exception
                    )
                }
            }

            addOnCanceledListener {
                continuation.cancel()
            }
        }
    }
}