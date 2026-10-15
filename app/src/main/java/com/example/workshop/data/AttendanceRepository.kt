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

        return attendanceDao.getAttendance(
            staffId = staffId,
            date = date
        )
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

    private suspend fun com.google.android.gms.tasks.Task<Void>.await() {

        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener {

                if (continuation.isActive) {
                    continuation.resume(Unit)
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