package com.resqedge.lite.mesh

enum class ReconnectAction {
    RequestKnownEndpoint,
    CycleDiscovery,
    WaitForPeer,
    Nothing
}

object ReconnectPolicy {
    const val DELAY_ATTEMPT_1 = 500L
    const val DELAY_ATTEMPT_2 = 2000L
    const val DELAY_ATTEMPT_3 = 5000L
    const val DELAY_ATTEMPT_4 = 10000L
    const val DELAY_ATTEMPT_5 = 20000L
    const val DELAY_ATTEMPT_6 = 40000L
    const val DELAY_DEFAULT = 60000L

    const val NON_INITIATOR_EXTRA_DELAY_MS = 1500L
    const val CYCLE_DISCOVERY_TRIGGER_MS = 3000L
    const val COOLDOWN_FIRST_MINUTE_MS = 5000L
    const val COOLDOWN_AFTER_MINUTE_MS = 30000L
    const val ONE_MINUTE_MS = 60000L
    const val ADVERTISING_RESTART_THRESHOLD_MS = 60000L

    fun delayBeforeAttemptMs(attempt: Int): Long {
        return when (attempt) {
            1 -> DELAY_ATTEMPT_1
            2 -> DELAY_ATTEMPT_2
            3 -> DELAY_ATTEMPT_3
            4 -> DELAY_ATTEMPT_4
            5 -> DELAY_ATTEMPT_5
            6 -> DELAY_ATTEMPT_6
            else -> DELAY_DEFAULT
        }
    }

    fun action(
        sinceDisconnectMs: Long,
        attempt: Int,
        knownEndpointId: String?,
        endpointStillDiscovered: Boolean,
        iAmInitiator: Boolean
    ): ReconnectAction {
        if (sinceDisconnectMs >= CYCLE_DISCOVERY_TRIGGER_MS && sinceDisconnectMs < CYCLE_DISCOVERY_TRIGGER_MS + 1000L && attempt <= 2) {
            return ReconnectAction.CycleDiscovery
        }

        val baseDelay = delayBeforeAttemptMs(attempt)
        var requiredDelay = baseDelay
        if (attempt == 1 && !iAmInitiator) {
            requiredDelay += NON_INITIATOR_EXTRA_DELAY_MS
        }

        if (sinceDisconnectMs < requiredDelay) {
            return ReconnectAction.Nothing
        }

        if (knownEndpointId != null && endpointStillDiscovered) {
            return ReconnectAction.RequestKnownEndpoint
        }

        return ReconnectAction.WaitForPeer
    }

    fun canCycleDiscovery(sinceDisconnectMs: Long, lastCycleAgoMs: Long): Boolean {
        val cooldown = if (sinceDisconnectMs <= ONE_MINUTE_MS) COOLDOWN_FIRST_MINUTE_MS else COOLDOWN_AFTER_MINUTE_MS
        return lastCycleAgoMs >= cooldown
    }

    fun canRestartAdvertising(failedAttemptsDurationMs: Long): Boolean {
        return failedAttemptsDurationMs >= ADVERTISING_RESTART_THRESHOLD_MS
    }
}
