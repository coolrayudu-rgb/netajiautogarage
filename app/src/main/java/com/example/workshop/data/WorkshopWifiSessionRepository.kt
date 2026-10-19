
package com.example.workshop.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WorkshopWifiSessionRepository(
    private val dao: WorkshopWifiSessionDao
) {

    // Firestore instance used to sync workshop Wi-Fi sessions
    // between staff devices and the admin dashboard.
    private val firestore = FirebaseFirestore.getInstance()

    // Generate a consistent Firestore document ID for each session.
    // Spaces and colons are replaced to keep the ID predictable.
    private fun documentId(
        staffId: Int,
        date: String,
        connectedAt: String
    ): String {
        return "${staffId}_${date}_${connectedAt}"
            .replace(" ", "_")
            .replace(":", "-")
    }

    // Return the active session from the local database.
    // An active session has no disconnection time.
    suspend fun getActiveSession(
        staffId: Int,
        date: String
    ): WorkshopWifiSession? {
        return dao.getActiveSession(
            staffId = staffId,
            date = date
        )
    }

    // Start a new workshop Wi-Fi session.
    // Save it locally first, then sync it to Firestore.
    suspend fun startSession(
        staffId: Int,
        date: String,
        connectedAt: String
    ): Long {

        val session = WorkshopWifiSession(
            staffId = staffId,
            date = date,
            connectedAt = connectedAt,
            disconnectedAt = null
        )

        // Save the session in the local Room database.
        val localId = dao.insert(session)

        // Prepare the session data for Firestore.
        val data = hashMapOf(
            "staffId" to staffId,
            "date" to date,
            "connectedAt" to connectedAt,
            "disconnectedAt" to null
        )

        // Upload the session using the same ID used for updates.
        firestore
            .collection("workshopWifiSessions")
            .document(
                documentId(
                    staffId,
                    date,
                    connectedAt
                )
            )
            .set(data)
            .await()

        return localId
    }

    // End an existing session and save its disconnection time.
    suspend fun endSession(
        session: WorkshopWifiSession,
        disconnectedAt: String
    ) {

        // Update the local Room database.
        dao.update(
            session.copy(
                disconnectedAt = disconnectedAt
            )
        )

        // Prepare the updated session for Firestore.
        val data = hashMapOf(
            "staffId" to session.staffId,
            "date" to session.date,
            "connectedAt" to session.connectedAt,
            "disconnectedAt" to disconnectedAt
        )

        // Update the same Firestore document created at check-in.
        firestore
            .collection("workshopWifiSessions")
            .document(
                documentId(
                    session.staffId,
                    session.date,
                    session.connectedAt
                )
            )
            .set(data)
            .await()
    }

    // Get all workshop Wi-Fi sessions for one staff member
    // on a specific date. This is used to calculate workshop time.
    suspend fun getSessionsForDate(
        staffId: Int,
        date: String
    ): List<WorkshopWifiSession> {

        return try {
            // Query Firestore so this works across different devices.
            val snapshot = firestore
                .collection("workshopWifiSessions")
                .whereEqualTo("staffId", staffId)
                .whereEqualTo("date", date)
                .get()
                .awaitQuery()

            // Convert Firestore documents into session objects.
            snapshot.documents.mapNotNull { document ->

                val savedStaffId =
                    document.getLong("staffId")?.toInt()
                        ?: return@mapNotNull null

                val savedDate =
                    document.getString("date")
                        ?: return@mapNotNull null

                val connectedAt =
                    document.getString("connectedAt")
                        ?: return@mapNotNull null

                WorkshopWifiSession(
                    // Firestore sessions don't use the local Room ID.
                    id = 0,
                    staffId = savedStaffId,
                    date = savedDate,
                    connectedAt = connectedAt,
                    disconnectedAt =
                        document.getString("disconnectedAt")
                )
            }
        } catch (e: Exception) {
            // If Firestore is unavailable, use local data as a fallback.
            dao.getSessionsForDate(
                staffId = staffId,
                date = date
            )
        }
    }

    // Await Firestore write operations such as set().
    // This helper handles Task<Void>.
    private suspend fun Task<Void>.await() {
        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }

            addOnCanceledListener {
                continuation.cancel()
            }
        }
    }

    // Await Firestore read operations such as get().
    // QuerySnapshot contains the returned documents.
    private suspend fun Task<QuerySnapshot>.awaitQuery(): QuerySnapshot {
        return suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { snapshot ->
                if (continuation.isActive) {
                    continuation.resume(snapshot)
                }
            }

            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }

            addOnCanceledListener {
                continuation.cancel()
            }
        }
    }
}
