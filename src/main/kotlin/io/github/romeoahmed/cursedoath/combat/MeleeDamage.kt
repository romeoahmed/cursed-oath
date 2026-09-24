package io.github.romeoahmed.cursedoath.combat

import kotlin.math.pow

/** Pre-mitigation damage units; positive strikes use at least two units for Black Flash. */
object MeleeDamage {
    private const val REINFORCEMENT = 1.15f
    private const val BLACK_FLASH_EXPONENT = 2.5
    private const val MINIMUM_POWER = 2f

    fun resolve(
        damage: Float,
        blackFlash: Boolean,
    ): Float =
        when {
            damage <= 0f -> {
                0f
            }

            blackFlash -> {
                damage
                    .coerceAtLeast(MINIMUM_POWER)
                    .toDouble()
                    .pow(BLACK_FLASH_EXPONENT)
                    .toFloat()
            }

            else -> {
                damage * REINFORCEMENT
            }
        }
}
