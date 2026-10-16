package com.resqedge.lite.mesh

import org.junit.Assert.*
import org.junit.Test

class ConnectionPolicyTest {

    @Test
    fun testShouldInitiateDifferentNames() {
        val name1 = "ResQ-AAAA"
        val name2 = "ResQ-BBBB"
        val i1 = shouldInitiate(name1, name2)
        val i2 = shouldInitiate(name2, name1)
        assertTrue(i1 xor i2)
    }

    @Test
    fun testShouldInitiateEqualNames() {
        val name = "ResQ-AAAA"
        assertFalse(shouldInitiate(name, name))
    }
}
