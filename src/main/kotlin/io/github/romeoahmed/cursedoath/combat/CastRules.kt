package io.github.romeoahmed.cursedoath.combat

import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.server.level.ServerPlayer

internal object CastRules {
    fun qualified(
        player: ServerPlayer,
        technique: Technique,
    ): Boolean {
        val profile = player.getAttachedOrCreate(SorcererData.PROFILE)
        return when {
            technique.domain -> profile.barriers
            technique == Technique.HEAL -> profile.selfHealing
            technique.requiresReversal -> profile.reversal
            else -> true
        }
    }

    fun available(
        player: ServerPlayer,
        technique: Technique,
    ): Boolean {
        if (!player.isAlive || player.isSpectator || Domains.isOverloaded(player)) return false
        if (technique.domain) return true
        return !player.isUsingItem && !player.isPassenger && !player.isFallFlying && !player.isSwimming
    }

    fun rejection(
        fighter: Fighter,
        technique: Technique,
    ): String? {
        val player = fighter.player
        if (!qualified(player, technique)) return "qualification"
        if (Domains.isOverloaded(player)) return "overloaded"
        if (technique.innate) innateRejection(fighter)?.let { return it }
        return when {
            fighter.cast != null || fighter.recovery > 0 -> {
                "busy"
            }

            !available(player, technique) -> {
                "hands"
            }

            technique == Technique.HEAL && player.health >= player.maxHealth -> {
                "healthy"
            }

            technique == Technique.INFINITY && fighter.energy.available < Defense.MAINTENANCE -> {
                "energy"
            }

            else -> {
                null
            }
        }
    }

    private fun innateRejection(fighter: Fighter): String? =
        when {
            fighter.defense.amplification -> "amplification"
            fighter.burnout > 0 && Domains.ownedBy(fighter.player) == null -> "burnout"
            else -> null
        }
}
