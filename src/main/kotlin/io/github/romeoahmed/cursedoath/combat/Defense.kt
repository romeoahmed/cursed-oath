package io.github.romeoahmed.cursedoath.combat

import io.github.romeoahmed.cursedoath.domain.DomainSounds
import io.github.romeoahmed.cursedoath.technique.InfinityDefense
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource

internal class Defense(
    private val player: ServerPlayer,
) {
    var infinity = false
    var amplification = false
        private set
    var simple = 0
        private set
    private var erodedAt = Long.MIN_VALUE
    val sustained: Boolean get() = infinity || amplification

    fun toggle(
        technique: Technique,
        energy: CursedEnergy,
    ): CursedEnergy {
        if (technique == Technique.SIMPLE_DOMAIN) {
            if (simple > 0) {
                simple = 0
            } else {
                val paid = energy.spend(technique.cost.release) ?: return energy
                simple = SIMPLE_STRENGTH
                amplification = false
                return paid
            }
        } else {
            amplification = !amplification
            if (amplification) {
                infinity = false
                simple = 0
            }
        }
        return energy
    }

    fun tick(energy: CursedEnergy): CursedEnergy {
        if (!sustained) return energy
        val paid = energy.spend(MAINTENANCE)
        if (paid == null) {
            clear()
            return energy
        }
        if (infinity) InfinityDefense.interceptProjectiles(player)
        return paid
    }

    fun erode() {
        val tick = player.level().gameTime
        if (erodedAt != tick) {
            val previous = simple
            simple = (simple - 1).coerceAtLeast(0)
            if (previous > 0 && simple == 0) {
                player.level().playSound(
                    null,
                    player.x,
                    player.y,
                    player.z,
                    DomainSounds.BREAK,
                    SoundSource.PLAYERS,
                    1f,
                    1f,
                )
            }
            erodedAt = tick
        }
    }

    fun clear() {
        infinity = false
        amplification = false
        simple = 0
    }

    companion object {
        const val MAINTENANCE = 2
        const val SIMPLE_STRENGTH = 100
        const val SIMPLE_RADIUS_SQUARED = 9.0
    }
}
