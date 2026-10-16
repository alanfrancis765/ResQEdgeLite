package com.resqedge.lite.mesh

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshPermissionsTest {

    @Test
    fun testPermissionsApi29() {
        val perms = requiredPermissions(29)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun testPermissionsApi30() {
        val perms = requiredPermissions(30)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun testPermissionsApi31() {
        val perms = requiredPermissions(31)
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            perms
        )
    }

    @Test
    fun testPermissionsApi33() {
        val perms = requiredPermissions(33)
        assertEquals(
            listOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            perms
        )
    }

    @Test
    fun testPermissionsApi34() {
        val perms = requiredPermissions(34)
        assertEquals(
            listOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            perms
        )
    }
}
