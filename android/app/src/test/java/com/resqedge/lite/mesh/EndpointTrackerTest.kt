package com.resqedge.lite.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointTrackerTest {
    @Test
    fun testFoundThenInitiate() {
        val tracker = EndpointTracker("A_MyName")
        val action = tracker.onFound("id1", "Z_TheirName", 1000L)
        assertEquals(Action.Initiate, action)
    }

    @Test
    fun testSecondFoundIgnoredWhileInProgress() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L) // goes to REQUESTING
        val action2 = tracker.onFound("id1", "Z_TheirName", 2000L)
        assertEquals(Action.Ignore, action2)
    }

    @Test
    fun testDisconnectedThenFoundAgain() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L) // REQUESTING
        tracker.onInitiated("id1", 1500L)
        tracker.onConnected("id1", 2000L)
        tracker.onDisconnected("id1", 3000L) // Records downtime start
        
        // found again
        val action = tracker.onFound("id1", "Z_TheirName", 4000L)
        assertEquals(Action.Initiate, action)
    }

    @Test
    fun testFailedRequestAllowsNewRequest() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L) // REQUESTING
        tracker.onRequestFailed("id1")
        
        val action = tracker.onFound("id1", "Z_TheirName", 2000L)
        assertEquals(Action.Initiate, action)
    }

    @Test
    fun testLostClearsState() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L)
        tracker.onLost("id1")
        
        val action = tracker.onFound("id1", "Z_TheirName", 2000L)
        assertEquals(Action.Initiate, action)
    }

    @Test
    fun testSweepResetsStuckRequest() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L) // REQUESTING
        
        val stuckBefore15s = tracker.sweep(16000L)
        assertEquals(0, stuckBefore15s.size) // 16000 - 1000 = 15000, not > 15000
        
        val stuckAfter15s = tracker.sweep(16001L)
        assertEquals(listOf("id1"), stuckAfter15s)
    }

    @Test
    fun testNonInitiatorFallbackAfter10s() {
        val tracker = EndpointTracker("Z_MyName") // won't initiate against A_TheirName
        val action1 = tracker.onFound("id1", "A_TheirName", 1000L)
        assertEquals(Action.WaitForPeer, action1)
        
        val action2 = tracker.onFound("id1", "A_TheirName", 2000L)
        assertEquals(Action.WaitForPeer, action2)
        
        // fallback requires 10s + random delay (0-1500ms). So 11501L elapsed is definitely > 10s + randomDelay
        val action3 = tracker.onFound("id1", "A_TheirName", 1000L + 11501L)
        assertEquals(Action.Initiate, action3)
    }

    @Test
    fun testDowntimeReportedOnNextConnection() {
        val tracker = EndpointTracker("A_MyName")
        tracker.onFound("id1", "Z_TheirName", 1000L)
        tracker.onConnected("id1", 2000L)
        tracker.onDisconnected("id1", 3000L) // down from 3000
        
        tracker.onFound("id1", "Z_TheirName", 4000L)
        val downtime = tracker.onConnected("id1", 5000L)
        assertEquals(2000L, downtime) // 5000 - 3000 = 2000
        
        // Next connection of same peer shouldn't report downtime unless disconnected again
        val downtime2 = tracker.onConnected("id1", 6000L)
        assertNull(downtime2)
    }
}
