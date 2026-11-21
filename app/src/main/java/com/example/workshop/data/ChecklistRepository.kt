package com.example.workshop.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * The staff daily workshop checklist.
 *
 * Saved on the phone (Room) first, then pushed to
 * Firestore: collection "checklists", one document per
 * staff member per day ("staffId_date"), with a map of
 * item name → ticked.
 */
class ChecklistRepository(
    private val checklistDao: ChecklistDao
) {

    private val checklists =
        FirebaseFirestore.getInstance()
            .collection("checklists")

    private fun documentId(staffId: Int, date: String) =
        "${staffId}_$date"

    /*
     * Ticked state for each item.
     * Firestore first, the phone's copy if offline.
     */
    suspend fun getChecklist(
        staffId: Int,
        date: String
    ): Map<String, Boolean> {

        val local =
            checklistDao.getChecklist(staffId, date)
                .associate { it.item to it.checked }

        val remote: Map<String, Boolean> =
            try {
                withTimeoutOrNull(10_000L) {
                    val snapshot =
                        checklists
                            .document(documentId(staffId, date))
                            .get()
                            .await()

                    val items = snapshot.get("items") as? Map<*, *>

                    items
                        ?.mapNotNull { (key, value) ->
                            val name = key as? String
                            val ticked = value as? Boolean
                            if (name != null && ticked != null) name to ticked else null
                        }
                        ?.toMap()
                        ?: emptyMap()
                }
            } catch (e: Exception) {
                null
            }
                ?: return local

        // Keep the phone's copy in step with Firestore.
        val now = WorkshopTime.now()
        remote.forEach { (item, checked) ->
            if (local[item] != checked) {
                checklistDao.upsert(
                    ChecklistItemEntry(staffId, date, item, checked, now)
                )
            }
        }

        // Items ticked offline that Firestore doesn't have yet.
        return local + remote
    }

    /*
     * Save one item. The phone is updated straight away;
     * Firestore is updated in the background and catches
     * up by itself if the phone is offline.
     */
    suspend fun setItem(
        staffId: Int,
        staffName: String,
        date: String,
        item: String,
        checked: Boolean
    ) {

        val now = WorkshopTime.now()

        checklistDao.upsert(
            ChecklistItemEntry(staffId, date, item, checked, now)
        )

        checklists
            .document(documentId(staffId, date))
            .set(
                hashMapOf(
                    "staffId" to staffId,
                    "staffName" to staffName,
                    "date" to date,
                    "updatedAt" to now,
                    "items" to mapOf(item to checked)
                ),
                SetOptions.merge()
            )
        // Not awaited on purpose: Firestore queues the
        // write while offline and sends it later.
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
}
