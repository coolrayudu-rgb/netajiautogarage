package com.example.workshop.data

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * An alert that a checked-in staff member has been
 * away from the workshop Wi-Fi for too long.
 *
 * level = 30 → sent to the admin
 * level = 60 → sent to the admin and every logged-in user
 */
data class AwayAlert(
    val id: String,
    val staffId: Int,
    val staffName: String,
    val date: String,
    val awaySince: String,
    val level: Int,
    val createdAtMillis: Long
)

/*
 * Alerts are shared through Firestore (collection
 * "awayAlerts"), so any phone that notices a staff
 * member is away can raise the alert and every
 * logged-in phone receives it.
 *
 * The document id is fixed per staff / away period /
 * level, so the same alert written by two phones is
 * stored (and notified) only once.
 */
class AwayAlertRepository {

    private val alerts =
        FirebaseFirestore.getInstance()
            .collection("awayAlerts")

    private fun documentId(
        staffId: Int,
        date: String,
        awaySince: String,
        level: Int
    ): String {
        return "${staffId}_${date}_${awaySince}_$level"
            .replace(" ", "_")
            .replace(":", "-")
    }

    /*
     * Create the alert if it doesn't exist yet.
     */
    suspend fun raiseAlert(
        staffId: Int,
        staffName: String,
        date: String,
        awaySince: String,
        level: Int
    ) {

        val document =
            alerts.document(
                documentId(staffId, date, awaySince, level)
            )

        if (document.get().await().exists()) {
            return
        }

        document
            .set(
                hashMapOf(
                    "staffId" to staffId,
                    "staffName" to staffName,
                    "date" to date,
                    "awaySince" to awaySince,
                    "level" to level,
                    "createdAtMillis" to System.currentTimeMillis()
                )
            )
            .await()
    }

    /*
     * Listen for new alerts for one day.
     *
     * Only alerts created in the last few minutes are
     * passed on, so old alerts aren't repeated when a
     * user logs in.
     */
    fun listen(
        date: String,
        onAlert: (AwayAlert) -> Unit
    ): ListenerRegistration {

        return alerts
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()

                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->

                        val document = change.document

                        val alert =
                            AwayAlert(
                                id = document.id,
                                staffId =
                                    document.getLong("staffId")?.toInt()
                                        ?: return@forEach,
                                staffName =
                                    document.getString("staffName") ?: "",
                                date =
                                    document.getString("date") ?: date,
                                awaySince =
                                    document.getString("awaySince") ?: "",
                                level =
                                    document.getLong("level")?.toInt()
                                        ?: return@forEach,
                                createdAtMillis =
                                    document.getLong("createdAtMillis")
                                        ?: now
                            )

                        if (now - alert.createdAtMillis <= RECENT_MILLIS) {
                            onAlert(alert)
                        }
                    }
            }
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
                    continuation.resumeWithException(exception)
                }
            }

            addOnCanceledListener {
                continuation.cancel()
            }
        }
    }

    companion object {
        private const val RECENT_MILLIS = 10 * 60 * 1000L
    }
}
