package io.github.romeoahmed.cursedoath.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CursedEnergyTest {
    @Test
    fun `competing casts cannot spend reserved energy`() {
        val prepared = assertNotNull(CursedEnergy(100).prepare(10, 70))
        assertEquals(90, prepared.current)
        assertEquals(20, prepared.available)
        assertNull(prepared.prepare(10, 11))
        assertNull(prepared.spend(21))
        assertEquals(CursedEnergy(20), prepared.release(70))
    }

    @Test
    fun `cancellation releases reservation without refunding startup`() {
        val prepared = assertNotNull(CursedEnergy(100).prepare(10, 70))
        assertEquals(CursedEnergy(90), prepared.cancel(70))
        assertFailsWith<IllegalArgumentException> { prepared.cancel(71) }
    }

    @Test
    fun `cost arithmetic cannot overflow into free energy`() {
        assertNull(CursedEnergy().prepare(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(CursedEnergy(), CursedEnergy(1).recover(Int.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { CursedEnergy().spend(-1) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy(5, 6) }
    }

    @Test
    fun `multiple reservations settle independently and regeneration preserves obligations`() {
        val first = assertNotNull(CursedEnergy(100).prepare(10, 30))
        val second = assertNotNull(first.prepare(5, 40))
        assertEquals(CursedEnergy(85, 70), second)
        assertEquals(CursedEnergy(45, 30), second.release(40))
        assertEquals(CursedEnergy(85, 30), second.cancel(40))
        assertEquals(CursedEnergy(1000, 70), second.recover(1000))
        assertEquals(CursedEnergy(0), CursedEnergy(100).spend(100))
    }

    @Test
    fun `invalid costs and balances cannot enter the ledger`() {
        assertFailsWith<IllegalArgumentException> { CursedEnergy(-1) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy(1001) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy().prepare(-1, 0) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy().prepare(0, -1) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy().release(1) }
        assertFailsWith<IllegalArgumentException> { CursedEnergy().recover(-1) }
    }
}
