package com.resqedge.lite.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GpsStatus { NO_PERMISSION, GPS_OFF, SEARCHING, FIX }

data class GpsState(
    val status: GpsStatus,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val ageSeconds: Long,
    val source: String,           // "gps", "network", "last known"
    val satellitesVisible: Int,
    val satellitesUsed: Int,
    val hint: String,
    val requestPermission: () -> Unit
)

private const val FRESH_FIX_AGE_S = 120L
private const val USABLE_FIX_AGE_S = 900L   // up to 15 minutes is shown as "last known"

private fun hasFineLocation(context: Context) =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

/** Monotonic age in seconds. Never uses the wall clock. */
private fun fixAgeSeconds(l: Location, nowNs: Long): Long =
    ((nowNs - l.elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0L)

/** Newer wins, except a network fix never replaces a GPS fix younger than 30 s. */
private fun better(candidate: Location, current: Location?, nowNs: Long): Location {
    if (current == null) return candidate
    val candidateIsGps = candidate.provider == LocationManager.GPS_PROVIDER
    val currentIsGps = current.provider == LocationManager.GPS_PROVIDER
    if (!candidateIsGps && currentIsGps && fixAgeSeconds(current, nowNs) < 30) return current
    return if (candidate.elapsedRealtimeNanos >= current.elapsedRealtimeNanos) candidate else current
}

@Composable
fun rememberGps(): GpsState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var granted by remember { mutableStateOf(hasFineLocation(appContext)) }
    var gpsEnabled by remember { mutableStateOf(true) }
    var netEnabled by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf<Location?>(null) }
    var nowNs by remember { mutableStateOf(SystemClock.elapsedRealtimeNanos()) }
    var satVisible by remember { mutableStateOf(0) }
    var satUsed by remember { mutableStateOf(0) }
    var gpsCallbacks by remember { mutableStateOf(0) }
    var netCallbacks by remember { mutableStateOf(0) }
    var lastCallback by remember { mutableStateOf("never") }
    var errors by remember { mutableStateOf("none") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasFineLocation(appContext) }

    LaunchedEffect(Unit) {
        while (true) {
            nowNs = SystemClock.elapsedRealtimeNanos()
            delay(1000L)
        }
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val handler = Handler(Looper.getMainLooper())

        fun refresh() {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        fun accept(l: Location) {
            val now = SystemClock.elapsedRealtimeNanos()
            location = better(l, location, now)
            nowNs = now
            lastCallback = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        }

        fun addError(text: String) {
            errors = if (errors == "none") text else "$errors; $text"
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (l.provider == LocationManager.GPS_PROVIDER) gpsCallbacks++ else netCallbacks++
                accept(l)
            }
            override fun onProviderEnabled(provider: String) { refresh() }
            override fun onProviderDisabled(provider: String) { refresh() }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        errors = "none"
        refresh()

        // Each provider registers independently, so one failure never blocks the other.
        fun register(provider: String) {
            try {
                lm.getLastKnownLocation(provider)?.let { accept(it) }
                lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                addError("$provider: ${e.javaClass.simpleName} ${e.message}")
            }
        }
        val all = lm.allProviders
        if (all.contains(LocationManager.GPS_PROVIDER)) register(LocationManager.GPS_PROVIDER)
        if (all.contains(LocationManager.NETWORK_PROVIDER)) register(LocationManager.NETWORK_PROVIDER)

        val gnss: GnssStatus.Callback? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var used = 0
                    for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                    satVisible = status.satelliteCount
                    satUsed = used
                }
            }
        } else null
        if (gnss != null) {
            try {
                lm.registerGnssStatusCallback(gnss, handler)
            } catch (e: Exception) {
                addError("gnss: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        // If nothing arrived after 10 s, ask each enabled provider for a single fix (API 30+).
        val fallback = Runnable {
            if (Build.VERSION.SDK_INT >= 30 && location == null) {
                for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    try {
                        if (all.contains(p) && lm.isProviderEnabled(p)) {
                            lm.getCurrentLocation(p, null, ContextCompat.getMainExecutor(appContext)) { l ->
                                if (l != null) accept(l)
                            }
                        }
                    } catch (e: Exception) {
                        addError("current/$p: ${e.javaClass.simpleName} ${e.message}")
                    }
                }
            }
        }
        handler.postDelayed(fallback, 10_000L)

        onDispose {
            handler.removeCallbacks(fallback)
            try { lm.removeUpdates(listener) } catch (e: Exception) { }
            if (gnss != null) {
                try { lm.unregisterGnssStatusCallback(gnss) } catch (e: Exception) { }
            }
        }
    }

    val loc = location
    val age = if (loc != null) fixAgeSeconds(loc, nowNs) else Long.MAX_VALUE
    val usable = loc != null && age <= USABLE_FIX_AGE_S
    val status = when {
        !granted -> GpsStatus.NO_PERMISSION
        !gpsEnabled && !netEnabled -> GpsStatus.GPS_OFF
        !usable -> GpsStatus.SEARCHING
        else -> GpsStatus.FIX
    }
    val source = when {
        loc == null || !usable -> ""
        age > FRESH_FIX_AGE_S -> "last known"
        loc.provider == LocationManager.GPS_PROVIDER -> "gps"
        else -> "network"
    }

    val base = when {
        !granted -> "Allow precise location"
        !gpsEnabled && !netEnabled -> "Turn on location in settings"
        status == GpsStatus.SEARCHING ->
            if (satVisible == 0)
                "No satellites seen. Go outside or next to a window. With no internet the first fix can take several minutes."
            else
                "Satellites seen, waiting for a fix"
        age > FRESH_FIX_AGE_S -> "Last known location (${maxOf(1L, age / 60)} min old)"
        else -> "Live fix acquired ($source)"
    }
    val debug = "Providers: gps=${if (gpsEnabled) "on" else "off"}, network=${if (netEnabled) "on" else "off"}\n" +
        "Callbacks: gps $gpsCallbacks, network $netCallbacks, last $lastCallback\n" +
        "Best fix: " + (if (loc == null) "none" else "${loc.provider}, ${age}s old, ${Math.round(loc.accuracy)} m") + "\n" +
        "Errors: $errors"

    return GpsState(
        status = status,
        lat = loc?.latitude ?: 0.0,
        lon = loc?.longitude ?: 0.0,
        accuracyM = loc?.accuracy ?: 0f,
        ageSeconds = if (age == Long.MAX_VALUE) 0L else age,
        source = source,
        satellitesVisible = satVisible,
        satellitesUsed = satUsed,
        hint = base + "\n" + debug,
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
