package com.example.workshop.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WorkshopWifiSessionRepository(
    private val dao: WorkshopWifiSessionDao
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private fun documentId(
        staffId: Int,
        date: String,
        connectedAt: String
    ): String {

        return "${staffId}_${date}_${connectedAt}"
            .replace(" ", "_")
            .replace(":", "-")
    }

    suspend fun getActiveSession(
        staffId: Int,
        date: String
    ): WorkshopWifiSession? {

        return dao.getActiveSession(
            staffId = staffId,
            date = date
        )
    }

    suspend fun startSession(
        staffId: Int,
        date: String,
        connectedAt: String
    ): Long {

        val session =
            WorkshopWifiSession(
                staffId = staffId,
                date = date,
                connectedAt = connectedAt,
                disconnectedAt = null
            )

        // Save locally
        val localId =
            dao.insert(session)

        // Save to Firestore
        val data =
            hashMapOf(
                "staffId" to staffId,
                "date" to date,
                "connectedAt" to connectedAt,
                "disconnectedAt" to null
            )

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

    suspend fun endSession(
        session: WorkshopWifiSession,
        disconnectedAt: String
    ) {

        // Update locally
        dao.update(
            session.copy(
                disconnectedAt =
                    disconnectedAt
            )
        )

        // Update Firestore
        val data =
            hashMapOf(
                "staffId" to session.staffId,
                "date" to session.date,
                "connectedAt" to session.connectedAt,
                "disconnectedAt" to disconnectedAt
            )

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

    suspend fun getSessionsForDate(
        staffId: Int,
        date: String
    ): List<WorkshopWifiSession> {

        return dao.getSessionsForDate(
            staffId = staffId,
            date = date
        )
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