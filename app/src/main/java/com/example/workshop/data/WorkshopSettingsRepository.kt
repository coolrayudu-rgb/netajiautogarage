package com.example.workshop.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * App-wide workshop settings.
 *
 * Stored in Firestore at settings/workshop so the admin
 * can change them and every phone picks them up.
 * The last known value is cached on the phone so the
 * Wi-Fi check still works offline.
 */
class WorkshopSettingsRepository(
    context: Context
) {

    private val firestore =
        FirebaseFirestore.getInstance()

    private val settingsDocument =
        firestore
            .collection("settings")
            .document("workshop")

    private val prefs =
        context.applicationContext.getSharedPreferences(
            "garage_settings",
            Context.MODE_PRIVATE
        )

    /*
     * The required workshop Wi-Fi name, from the
     * local cache. Never blocks.
     */
    fun cachedWifiName(): String {
        return prefs.getString(KEY_WIFI_NAME, null)
            ?: DEFAULT_WIFI_NAME
    }

    /*
     * Read the Wi-Fi name from Firestore and cache it.
     * Returns the cached value if Firestore is unavailable.
     */
    suspend fun refreshWifiName(): String {

        return try {

            val snapshot =
                settingsDocument.get().await()

            val name =
                snapshot.getString("wifiName")

            if (!name.isNullOrBlank()) {
                cache(name)
            }

            cachedWifiName()

        } catch (e: Exception) {
            cachedWifiName()
        }
    }

    /*
     * Admin only: change the required Wi-Fi name.
     */
    suspend fun saveWifiName(name: String) {

        val cleanName = name.trim()

        require(cleanName.isNotEmpty()) {
            "Wi-Fi name can't be empty"
        }

        // Firestore only confirms a write once it reaches
        // the server, so give up after 15 seconds offline.
        withTimeout(15_000L) {
            settingsDocument
                .set(
                    hashMapOf("wifiName" to cleanName),
                    SetOptions.merge()
                )
                .await()
        }

        cache(cleanName)
    }

    private fun cache(name: String) {
        prefs.edit()
            .putString(KEY_WIFI_NAME, name)
            .apply()
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
        // Used until the admin sets a name in the app.
        const val DEFAULT_WIFI_NAME = "TP-Link_0E14"
        private const val KEY_WIFI_NAME = "wifi_name"
    }
}
