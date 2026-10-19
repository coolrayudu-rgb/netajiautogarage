package com.example.workshop.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StaffRepository(
    private val staffDao: StaffDao
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    /*
     * LOGIN
     *
     * Firebase is the main source.
     * Room is used if Firebase is unavailable.
     */
    suspend fun login(
        username: String,
        password: String
    ): Staff? {

        return try {

            val snapshot =
                firestore
                    .collection("staff")
                    .whereEqualTo(
                        "username",
                        username
                    )
                    .whereEqualTo(
                        "password",
                        password
                    )
                    .whereEqualTo(
                        "active",
                        true
                    )
                    .limit(1)
                    .get()
                    .await()

            if (!snapshot.isEmpty) {

                val document =
                    snapshot.documents.first()

                val staff =
                    documentToStaff(
                        document
                    )

                // Save Firebase staff locally
                saveToRoom(staff)

                staff

            } else {

                null
            }

        } catch (e: Exception) {

            // Firebase unavailable
            // → try local Room

            staffDao.login(
                username = username,
                password = password
            )
        }
    }
    suspend fun clearLocalStaff() {
        staffDao.deleteAllStaff()
    }

    suspend fun syncStaffFromFirebase(): List<Staff> {

        val snapshot =
            firestore
                .collection("staff")
                .get()
                .await()

        val firebaseStaff =
            snapshot.documents.map {
                documentToStaff(it)
            }

        // Remove old local staff
        staffDao.deleteAllStaff()

        // Save Firebase staff locally
        firebaseStaff.forEach { staff ->
            staffDao.insert(staff)
        }

        return firebaseStaff.sortedBy {
            it.name
        }
    }
    /*
     * GET ALL STAFF
     *
     * Firebase → Room
     */
    suspend fun getAllStaff(): List<Staff> {

        return try {

            syncStaffFromFirebase()

        } catch (e: Exception) {

            // Firebase unavailable
            // Use local Room cache
            staffDao.getAllStaff()
        }
    }

    /*
     * GET STAFF BY ID
     */
    suspend fun getStaffById(
        id: Int
    ): Staff? {

        return try {

            val snapshot =
                firestore
                    .collection("staff")
                    .document(id.toString())
                    .get()
                    .await()

            if (snapshot.exists()) {

                val staff =
                    documentToStaff(
                        snapshot
                    )

                saveToRoom(staff)

                staff

            } else {

                null
            }

        } catch (e: Exception) {

            staffDao.getStaffById(id)
        }
    }

    /*
     * ADD STAFF
     *
     * Firebase generates the central ID.
     */
    suspend fun addStaff(
        staff: Staff
    ): Staff {

        /*
         * Find the next available central ID.
         */
        val snapshot =
            firestore
                .collection("staff")
                .get()
                .await()

        val nextId =
            (
                    snapshot.documents
                        .mapNotNull {
                            it.getLong("id")
                                ?.toInt()
                        }
                        .maxOrNull()
                        ?: 0
                    ) + 1

        val newStaff =
            staff.copy(
                id = nextId
            )

        val data =
            hashMapOf(
                "id" to newStaff.id,
                "name" to newStaff.name,
                "username" to newStaff.username,
                "password" to newStaff.password,
                "active" to newStaff.active
            )

        /*
         * Save to Firebase.
         */
        firestore
            .collection("staff")
            .document(
                newStaff.id.toString()
            )
            .set(data)
            .await()

        /*
         * Save same ID locally.
         */
        saveToRoom(newStaff)

        return newStaff
    }

    /*
     * UPDATE STAFF
     */
    suspend fun updateStaff(
        staff: Staff
    ) {

        val data =
            hashMapOf(
                "id" to staff.id,
                "name" to staff.name,
                "username" to staff.username,
                "password" to staff.password,
                "active" to staff.active
            )

        firestore
            .collection("staff")
            .document(
                staff.id.toString()
            )
            .set(data)
            .await()

        saveToRoom(staff)
    }

    /*
     * DELETE STAFF
     */
    suspend fun deleteStaff(
        staff: Staff
    ) {

        firestore
            .collection("staff")
            .document(
                staff.id.toString()
            )
            .delete()
            .await()

        staffDao.delete(staff)
    }

    /*
     * Convert Firebase document → Staff
     */
    private fun documentToStaff(
        document:
        com.google.firebase.firestore.DocumentSnapshot
    ): Staff {

        return Staff(
            id =
                document.getLong("id")
                    ?.toInt()
                    ?: document.id.toInt(),

            name =
                document.getString("name")
                    ?: "",

            username =
                document.getString("username")
                    ?: "",

            password =
                document.getString("password")
                    ?: "",

            active =
                document.getBoolean("active")
                    ?: true
        )
    }

    /*
     * Save Firebase staff into Room.
     *
     * If the staff already exists,
     * update it.
     */
    private suspend fun saveToRoom(
        staff: Staff
    ) {

        val existing =
            staffDao.getStaffById(
                staff.id
            )

        if (existing == null) {

            staffDao.insert(staff)

        } else {

            staffDao.update(staff)
        }
    }

    /*
     * Firebase Task → Kotlin suspend
     */
    private suspend fun <T>
            com.google.android.gms.tasks.Task<T>.await(): T {

        return suspendCancellableCoroutine {
                continuation ->

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