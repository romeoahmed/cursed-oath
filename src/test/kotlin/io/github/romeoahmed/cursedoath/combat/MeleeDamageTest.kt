package io.github.romeoahmed.cursedoath.combat

import kotlin.test.Test
import kotlin.test.assertEquals

class MeleeDamageTest {
    @Test
    fun `black flash uses power growth and strengthens bare hands`() {
        assertEquals(32f, MeleeDamage.resolve(4f, true))
        assertEquals(129.64181f, MeleeDamage.resolve(7f, true), 0.0001f)
        assertEquals(5.656854f, MeleeDamage.resolve(1f, true), 0.000001f)
    }

    @Test
    fun `ordinary reinforcement and zero damage stay separate from black flash`() {
        assertEquals(4.6f, MeleeDamage.resolve(4f, false))
        for (blackFlash in listOf(false, true)) {
            assertEquals(0f, MeleeDamage.resolve(0f, blackFlash))
            assertEquals(0f, MeleeDamage.resolve(-1f, blackFlash))
        }
    }
}
