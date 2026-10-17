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
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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

private const val MAX_FRESH_FIX_AGE_S = 120L
private const val MAX_USABLE_FIX_AGE_S = 900L // 15 minutes for last known

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberGps(): GpsState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var granted by remember { mutableStateOf(hasLocationPermission(appContext)) }
    var locationProvidersEnabled by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf<Location?>(null) }
    var nowNs by remember { mutableStateOf(SystemClock.elapsedRealtimeNanos()) }
    var satellitesVisible by remember { mutableStateOf(0) }
    var satellitesUsed by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        granted = hasLocationPermission(appContext)
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowNs = SystemClock.elapsedRealtimeNanos()
            delay(1000L)
        }
    }

    DisposableEffect(granted) {
        if (!granted) {
            onDispose {}
        } else {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(l: Location) {
                    val candidates = listOfNotNull(location, l)
                    val best = GpsPolicy.chooseBestFix(candidates, SystemClock.elapsedRealtimeNanos(), 900_000L)
                    if (best != null) {
                        location = best
                        nowNs = SystemClock.elapsedRealtimeNanos()
                    }
                }
                override fun onProviderEnabled(provider: String) {
                    locationProvidersEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }
                override fun onProviderDisabled(provider: String) {
                    locationProvidersEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val visible = status.satelliteCount
                        var used = 0
                        for (i in 0 until visible) {
                            if (status.usedInFix(i)) {
                                used++
                            }
                        }
                        satellitesVisible = visible
                        satellitesUsed = used
                    }
                }
            } else null

            try {
                val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                locationProvidersEnabled = gpsEnabled || netEnabled

                val candidates = mutableListOf<Location>()
                if (gpsEnabled) {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { candidates.add(it) }
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener)
                }
                if (netEnabled) {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { candidates.add(it) }
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener)
                }

                val bestSeed = GpsPolicy.chooseBestFix(candidates, SystemClock.elapsedRealtimeNanos(), 900_000L)
                if (bestSeed != null) {
                    location = bestSeed
                }

                if (gnssCallback != null && ActivityCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    lm.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
                }
            } catch (e: SecurityException) {
                // permission revoked
            }

            onDispose {
                try {
                    lm.removeUpdates(listener)
                    if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        lm.unregisterGnssStatusCallback(gnssCallback)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    val loc = location
    val nowMs = System.currentTimeMillis()
    val ageSeconds = if (loc != null) {
        if (loc.time > 0) {
            (nowMs - loc.time) / 1000L
        } else {
            (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000_000L
        }
    } else {
        Long.MAX_VALUE
    }

    val source = when {
        loc == null -> ""
        ageSeconds <= MAX_FRESH_FIX_AGE_S -> {
            if (loc.provider == LocationManager.GPS_PROVIDER) "gps" else "network"
        }
        else -> "last known"
    }

    val isUsable = loc != null && ageSeconds <= MAX_USABLE_FIX_AGE_S
    val status = when {
        !granted -> GpsStatus.NO_PERMISSION
        !locationProvidersEnabled -> GpsStatus.GPS_OFF
        !isUsable -> GpsStatus.SEARCHING
        else -> GpsStatus.FIX
    }

    val hint = when {
        !granted -> "Allow precise location"
        !locationProvidersEnabled -> "Turn on location in settings"
        status == GpsStatus.SEARCHING -> {
            if (satellitesVisible == 0) {
                "No satellites seen. Go outside or next to a window. With no internet the first fix can take several minutes."
            } else {
                "Satellites seen, waiting for a fix"
            }
        }
        else -> {
            if (ageSeconds > MAX_FRESH_FIX_AGE_S) {
                "Last known location (${Math.max(1, ageSeconds / 60)} min old)"
            } else {
                "Live fix acquired ($source)"
            }
        }
    }

    return GpsState(
        status = status,
        lat = loc?.latitude ?: 0.0,
        lon = loc?.longitude ?: 0.0,
        accuracyM = loc?.accuracy ?: 0f,
        ageSeconds = if (ageSeconds == Long.MAX_VALUE) 0L else ageSeconds,
        source = source,
        satellitesVisible = satellitesVisible,
        satellitesUsed = satellitesUsed,
        hint = hint,
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
