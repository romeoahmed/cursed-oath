package io.github.romeoahmed.cursedoath.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class RequestGateTest {
    @Test
    void rejectsDuplicatesOldConnectionsAndNegativeSequences() {
        var gate = new RequestGate();
        assertFalse(gate.accept(UUID.randomUUID(), 0, 1));
        assertFalse(gate.accept(gate.session(), -1, 1));
        assertTrue(gate.accept(gate.session(), 0, 1));
        assertFalse(gate.accept(gate.session(), 0, 2));
        var reconnect = new RequestGate();
        assertFalse(reconnect.accept(gate.session(), 1, 2));
        assertTrue(reconnect.accept(reconnect.session(), 0, 2));
    }

    @Test
    void rejectedFloodCannotBeReplayedOnNextTick() {
        var gate = new RequestGate();
        for (int i = 0; i < 4; i++) assertTrue(gate.accept(gate.session(), i, 1));
        assertFalse(gate.accept(gate.session(), 4, 1));
        assertFalse(gate.accept(gate.session(), 4, 2));
        assertTrue(gate.accept(gate.session(), 5, 2));
    }

    @Test
    void invalidTrafficCannotConsumeBudgetOrAdvanceSequence() {
        var gate = new RequestGate();
        for (int i = 0; i < 10; i++) {
            assertFalse(gate.accept(UUID.randomUUID(), Long.MAX_VALUE, 1));
            assertFalse(gate.accept(gate.session(), -1, 1));
        }
        for (int i = 0; i < 4; i++) assertTrue(gate.accept(gate.session(), i, 1));
        assertTrue(gate.accept(gate.session(), Long.MAX_VALUE, 2));
        assertFalse(gate.accept(gate.session(), Long.MAX_VALUE, 3));
        assertFalse(gate.accept(gate.session(), 0, 3));
    }
}
