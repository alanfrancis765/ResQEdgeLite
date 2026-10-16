package com.resqedge.lite.mesh

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun requiredPermissions(sdkInt: Int): List<String> {
    return when {
        sdkInt >= 33 -> listOf(
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT"
        )
        sdkInt >= 31 -> listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        else -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

@Composable
fun rememberMeshPermissionsState(onResult: (Boolean) -> Unit = {}): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val perms = remember { requiredPermissions(android.os.Build.VERSION.SDK_INT) }
    var granted by remember {
        mutableStateOf(perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = perms.all { result[it] == true }
        granted = allGranted
        onResult(allGranted)
    }

    val request = {
        launcher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        val allGranted = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        granted = allGranted
        onResult(allGranted)
    }

    return granted to request
}
