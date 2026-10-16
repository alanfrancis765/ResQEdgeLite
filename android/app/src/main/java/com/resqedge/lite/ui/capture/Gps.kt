package com.resqedge.lite.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

enum class GpsStatus { NO_PERMISSION, GPS_OFF, SEARCHING, FIX }

data class GpsState(
    val status: GpsStatus,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracyM: Float = 0f,
    val ageSeconds: Long = 0,
    val requestPermission: () -> Unit = {}
)

private const val MAX_FIX_AGE_S = 120L

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberGps(): GpsState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationProvidersEnabled by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf<Location?>(null) }
    var nowNs by remember { mutableStateOf(SystemClock.elapsedRealtimeNanos()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        granted = hasLocationPermission(context)
    }

    // Refresh "now" every few seconds so the age of the fix keeps updating.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            nowNs = SystemClock.elapsedRealtimeNanos()
        }
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (location == null || l.time >= (location?.time ?: 0L)) {
                    location = l
                    nowNs = SystemClock.elapsedRealtimeNanos()
                }
            }
            override fun onProviderEnabled(provider: String) { locationProvidersEnabled = true }
            override fun onProviderDisabled(provider: String) {
                locationProvidersEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            locationProvidersEnabled = gpsEnabled || netEnabled

            val lastGps = if (gpsEnabled) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNet = if (netEnabled) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            if (bestLast != null) {
                location = bestLast
            }

            if (gpsEnabled) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener)
            }
            if (netEnabled) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener)
            }
        } catch (e: SecurityException) {
            // permission revoked while running
        }
        onDispose { lm.removeUpdates(listener) }
    }

    val loc = location
    val age = if (loc != null)
        ((nowNs - loc.elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0)
    else 0L

    val status = when {
        !granted -> GpsStatus.NO_PERMISSION
        !locationProvidersEnabled -> GpsStatus.GPS_OFF
        loc == null || age > MAX_FIX_AGE_S -> GpsStatus.SEARCHING
        else -> GpsStatus.FIX
    }

    return GpsState(
        status = status,
        lat = loc?.latitude ?: 0.0,
        lon = loc?.longitude ?: 0.0,
        accuracyM = loc?.accuracy ?: 0f,
        ageSeconds = age,
        requestPermission = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    )
}
