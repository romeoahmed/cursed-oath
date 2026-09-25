package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.combat.Defense
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity

internal object DomainProtection {
    private val eroding = linkedSetOf<Defense>()

    fun finishTick() {
        eroding.forEach(Defense::erode)
        eroding.clear()
    }

    fun protects(
        target: LivingEntity,
        erode: Boolean = false,
    ): Boolean {
        val level = target.level() as? ServerLevel ?: return false
        var protected = false
        for (fighter in CombatRuntime.fighters(level)) {
            if (fighter.defense.simple > 0 && fighter.player.isAlive &&
                fighter.player.distanceToSqr(target) <= Defense.SIMPLE_RADIUS_SQUARED
            ) {
                if (!erode) return true
                protected = true
                eroding.add(fighter.defense)
            }
        }
        return protected
    }
}
