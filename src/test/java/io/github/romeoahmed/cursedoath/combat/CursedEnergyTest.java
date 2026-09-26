package io.github.romeoahmed.cursedoath.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class CursedEnergyTest {
    @Test
    void competingCastsCannotSpendReservedEnergy() {
        var prepared = new CursedEnergy(100).prepare(10, 70);
        assertNotNull(prepared);
        assertEquals(90, prepared.current());
        assertEquals(20, prepared.available());
        assertNull(prepared.prepare(10, 11));
        assertNull(prepared.spend(21));
        assertEquals(new CursedEnergy(20), prepared.release(70));
    }

    @Test
    void exactBudgetCanBeReservedAndSettledWithoutOverdraft() {
        var prepared = new CursedEnergy(100).prepare(10, 90);
        assertNotNull(prepared);
        assertEquals(0, prepared.available());
        assertNull(prepared.spend(1));
        assertEquals(prepared, prepared.spend(0));
        assertEquals(new CursedEnergy(0), prepared.release(90));
        assertEquals(new CursedEnergy(90), prepared.cancel(90));
    }

    @Test
    void cancellationReleasesReservationWithoutRefundingStartup() {
        var prepared = new CursedEnergy(100).prepare(10, 70);
        assertNotNull(prepared);
        assertEquals(new CursedEnergy(90), prepared.cancel(70));
        assertThrows(IllegalArgumentException.class, () -> prepared.cancel(71));
    }

    @Test
    void costArithmeticCannotOverflowIntoFreeEnergy() {
        assertNull(new CursedEnergy().prepare(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(new CursedEnergy(), new CursedEnergy(1).recover(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy().spend(-1));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy(5, 6));
    }

    @Test
    void multipleReservationsSettleIndependentlyAndRegenerationPreservesObligations() {
        var first = new CursedEnergy(100).prepare(10, 30);
        assertNotNull(first);
        var second = first.prepare(5, 40);
        assertNotNull(second);
        assertEquals(new CursedEnergy(85, 70), second);
        assertEquals(new CursedEnergy(45, 30), second.release(40));
        assertEquals(new CursedEnergy(85, 30), second.cancel(40));
        assertEquals(new CursedEnergy(1000, 70), second.recover(1000));
        assertEquals(second, second.recover(0));
        assertEquals(new CursedEnergy(1000, 70), new CursedEnergy(1000, 70).recover(2));
        assertEquals(new CursedEnergy(0), new CursedEnergy(100).spend(100));
    }

    @Test
    void invalidCostsAndBalancesCannotEnterTheLedger() {
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy(-1));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy(1001));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy().prepare(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy().prepare(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy().release(1));
        assertThrows(IllegalArgumentException.class, () -> new CursedEnergy().recover(-1));
    }
}
