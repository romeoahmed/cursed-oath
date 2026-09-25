package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.domain.DomainInteractions
import io.github.romeoahmed.cursedoath.world.CleaveLattice
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Contact damage scales with armor and maximum health; lattice targets resolve after excavation. */
internal object CleaveContact {
    private const val RANGE = 4.0
    private const val ARMOR_SCALING = 2f
    private const val HEALTH_SCALING = 0.5f

    fun release(
        player: ServerPlayer,
        work: TerrainDestruction.Work,
    ): Vec3? {
        val hit = TechniqueCombat.contact(player, RANGE)
        val boundary =
            DomainInteractions.contact(
                player,
                player.eyePosition,
                hit?.location ?: player.eyePosition.add(player.lookAngle.scale(RANGE)),
            )
        if (boundary != null) {
            boundary.domain.damageShell(TechniqueTuning.CLEAVE_DAMAGE, !boundary.domain.contains(player.eyePosition))
            work.close()
            return boundary.point
        }
        if (hit == null || hit.type == HitResult.Type.MISS) {
            work.close()
            return null
        }
        val target = (hit as? EntityHitResult)?.entity as? LivingEntity
        if (target != null && TechniqueCombat.hasInfinity(target)) {
            work.close()
            return null
        }
        val lattice = CleaveLattice(hit.location, player.lookAngle)
        if (!player.level().hasLoadedChunks(lattice.bounds)) {
            work.close()
            return null
        }
        if (target != null) damage(player, target)
        work.cuts(lattice.cuts, hit.location)
        work.onComplete = {
            if (player.level().hasLoadedChunks(lattice.bounds)) {
                for (neighbor in player.level().getEntitiesOfClass(LivingEntity::class.java, lattice.bounds)) {
                    if (neighbor !== target && exposed(player, lattice, neighbor)) {
                        damage(player, neighbor)
                    }
                }
            }
        }
        return hit.location
    }

    private fun exposed(
        player: ServerPlayer,
        lattice: CleaveLattice,
        target: LivingEntity,
    ): Boolean {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return false
        return lattice.cuts.any { cut ->
            val contact = cut.contact(target.boundingBox) ?: return@any false
            if (DomainInteractions.contact(player, lattice.center, contact) != null) return@any false
            val hit =
                player.level().clipIncludingBorder(
                    ClipContext(
                        cut.source(contact),
                        contact,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.ANY,
                        player,
                    ),
                )
            hit.type == HitResult.Type.MISS
        }
    }

    private fun damage(
        player: ServerPlayer,
        target: LivingEntity,
    ) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return
        val level = player.level()
        target.hurtServer(level, level.damageSources().playerAttack(player), damageAmount(target))
    }

    fun damageAmount(target: LivingEntity): Float =
        (
            TechniqueTuning.CLEAVE_DAMAGE + target.armorValue * ARMOR_SCALING +
                target.maxHealth * HEALTH_SCALING
        ).coerceAtMost(TechniqueTuning.CLEAVE_MAX_DAMAGE)
}
