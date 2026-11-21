package com.example.workshop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/*
 * Reads the name of the Wi-Fi network the phone is on.
 *
 * Android only gives the Wi-Fi name to apps that have
 * location permission (or nearby Wi-Fi permission on
 * Android 13+).
 */
object WifiChecker {

    fun hasPermission(context: Context): Boolean {

        val location =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val nearbyWifi =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED

            return location || nearbyWifi
        }

        return location
    }

    /*
     * The current Wi-Fi name, or null if the phone isn't
     * on Wi-Fi or the name can't be read.
     */
    fun currentSsid(context: Context): String? {

        if (!hasPermission(context)) {
            android.util.Log.w("WIFI_CHECK", "No Wi-Fi permission")
            return null
        }

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network =
            connectivityManager.activeNetwork ?: return null

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return null

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        var ssid: String? = null

        // Newer Android versions: read it from the network.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ssid = (capabilities.transportInfo as? WifiInfo)?.ssid
        }

        // Fallback: WifiManager.
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {

            val wifiManager =
                context.applicationContext.getSystemService(
                    Context.WIFI_SERVICE
                ) as WifiManager

            @Suppress("DEPRECATION")
            ssid = wifiManager.connectionInfo?.ssid
        }

        ssid =
            ssid
                ?.removePrefix("\"")
                ?.removeSuffix("\"")

        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
            return null
        }

        return ssid
    }

    fun isOnWifi(
        context: Context,
        requiredName: String
    ): Boolean {

        val ssid = currentSsid(context)

        android.util.Log.d(
            "WIFI_CHECK",
            "SSID detected = [$ssid], expected = [$requiredName]"
        )

        return ssid != null && ssid == requiredName
    }
}
