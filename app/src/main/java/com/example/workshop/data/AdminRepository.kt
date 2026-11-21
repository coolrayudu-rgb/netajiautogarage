package com.example.workshop.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Admin accounts are stored in Firebase Firestore
 * (collection "admins", document id = username).
 *
 * Room keeps a copy of each admin that has logged in
 * on this phone, so login still works offline.
 */
class AdminRepository(
    private val adminDao: AdminDao
) {

    private val admins =
        FirebaseFirestore.getInstance()
            .collection("admins")

    /*
     * LOGIN
     *
     * Firebase first, Room if Firebase is unavailable.
     */
    suspend fun login(
        username: String,
        password: String
    ): Admin? {

        return try {

            ensureAdminExists()

            val snapshot =
                admins
                    .whereEqualTo("username", username)
                    .whereEqualTo("password", password)
                    .whereEqualTo("active", true)
                    .limit(1)
                    .get()
                    .await()

            if (snapshot.isEmpty) {
                null
            } else {
                val admin =
                    documentToAdmin(snapshot.documents.first())

                saveToRoom(admin)

                admin
            }

        } catch (e: Exception) {

            // Firebase unavailable → local copy
            adminDao.login(username, password)
        }
    }

    /*
     * Make sure at least one admin exists in Firebase.
     *
     * On the first run, the admin already saved on this
     * phone (older app versions created one) is uploaded.
     * If there is none, the default admin
     * (admin / admin123) is created in Firebase.
     * Change that password in Firebase before real use.
     */
    suspend fun ensureAdminExists() {

        val existing =
            admins.limit(1).get().await()

        if (!existing.isEmpty) {
            return
        }

        val local =
            adminDao.getByUsername(DEFAULT_USERNAME)

        addAdmin(
            local ?: Admin(
                username = DEFAULT_USERNAME,
                password = DEFAULT_PASSWORD,
                name = "Administrator",
                active = true
            )
        )
    }

    suspend fun addAdmin(admin: Admin) {

        val data =
            hashMapOf(
                "username" to admin.username,
                "password" to admin.password,
                "name" to admin.name,
                "active" to admin.active
            )

        admins
            .document(admin.username)
            .set(data)
            .await()

        saveToRoom(admin)
    }

    private fun documentToAdmin(
        document: DocumentSnapshot
    ): Admin {

        return Admin(
            username =
                document.getString("username")
                    ?: document.id,
            password =
                document.getString("password") ?: "",
            name =
                document.getString("name") ?: "",
            active =
                document.getBoolean("active") ?: true
        )
    }

    private suspend fun saveToRoom(admin: Admin) {

        val existing =
            adminDao.getByUsername(admin.username)

        if (existing == null) {
            adminDao.insert(admin.copy(id = 0))
        } else {
            adminDao.update(admin.copy(id = existing.id))
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
        private const val DEFAULT_USERNAME = "admin"
        private const val DEFAULT_PASSWORD = "admin123"
    }
}
