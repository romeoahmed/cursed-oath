package io.github.romeoahmed.cursedoath.network

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestGateTest {
    @Test
    fun `duplicates old connections and negative sequences are rejected`() {
        val gate = RequestGate()
        assertFalse(gate.accept(UUID.randomUUID(), 0, 1))
        assertFalse(gate.accept(gate.session, -1, 1))
        assertTrue(gate.accept(gate.session, 0, 1))
        assertFalse(gate.accept(gate.session, 0, 2))
        val reconnect = RequestGate()
        assertFalse(reconnect.accept(gate.session, 1, 2))
        assertTrue(reconnect.accept(reconnect.session, 0, 2))
    }

    @Test
    fun `a rejected flood cannot be replayed on the next tick`() {
        val gate = RequestGate()
        repeat(4) { assertTrue(gate.accept(gate.session, it.toLong(), 1)) }
        assertFalse(gate.accept(gate.session, 4, 1))
        assertFalse(gate.accept(gate.session, 4, 2))
        assertTrue(gate.accept(gate.session, 5, 2))
    }

    @Test
    fun `invalid traffic cannot consume a valid connection budget or advance its sequence`() {
        val gate = RequestGate()
        repeat(10) {
            assertFalse(gate.accept(UUID.randomUUID(), Long.MAX_VALUE, 1))
            assertFalse(gate.accept(gate.session, -1, 1))
        }
        repeat(4) { assertTrue(gate.accept(gate.session, it.toLong(), 1)) }
        assertTrue(gate.accept(gate.session, Long.MAX_VALUE, 2))
        assertFalse(gate.accept(gate.session, Long.MAX_VALUE, 3))
        assertFalse(gate.accept(gate.session, 0, 3))
    }
}
