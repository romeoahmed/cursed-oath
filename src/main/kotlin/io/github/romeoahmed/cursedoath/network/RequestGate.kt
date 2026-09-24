package io.github.romeoahmed.cursedoath.network

import java.util.UUID

/** A connection-scoped, constant-space replay and rate limit. TCP preserves request order. */
class RequestGate(
    val session: UUID = UUID.randomUUID(),
) {
    private var lastSequence = -1L
    private var windowTick = Long.MIN_VALUE
    private var requests = 0

    fun accept(
        session: UUID,
        sequence: Long,
        tick: Long,
    ): Boolean {
        if (session != this.session || sequence < 0 || sequence <= lastSequence) return false
        lastSequence = sequence
        if (windowTick != tick) {
            windowTick = tick
            requests = 0
        }
        return ++requests <= MAX_REQUESTS_PER_TICK
    }

    private companion object {
        const val MAX_REQUESTS_PER_TICK = 4
    }
}
