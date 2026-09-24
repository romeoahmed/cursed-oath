package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** Damage and repulsion use the cover present at impact; excavation follows under its own budget. */
internal object RedBlast {
    private const val RADIUS = TechniqueTuning.RED_RADIUS
    private const val DAMAGE = TechniqueTuning.RED_DAMAGE
    private const val KNOCKBACK = 4.5
    private const val LIFT = 0.8

    // ServerExplosion offsets exposure samples toward +X/+Z by less than half a block.
    private const val COVER_SAMPLE_MARGIN = 0.5

    fun impact(
        player: ServerPlayer,
        center: Vec3,
        direction: Vec3,
    ) {
        val level = player.level()
        val bounds = AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
        for (target in level.getEntitiesOfClass(LivingEntity::class.java, bounds)) {
            repel(player, target, center, direction)
        }
    }

    private fun repel(
        player: ServerPlayer,
        target: LivingEntity,
        center: Vec3,
        direction: Vec3,
    ) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return
        val distance = sqrt(target.boundingBox.distanceToSqr(center))
        val coverBounds = target.boundingBox.expandTowards(COVER_SAMPLE_MARGIN, 0.0, COVER_SAMPLE_MARGIN)
        if (distance >= RADIUS ||
            !player.level().hasLoadedChunks(AABB(center, center).minmax(coverBounds))
        ) {
            return
        }
        val strength = (1 - distance / RADIUS) * ServerExplosion.getSeenPercent(center, target)
        if (strength <= 0) return
        val damage = (DAMAGE * strength).toFloat()
        val level = player.level()
        val source = level.damageSources().playerAttack(player)
        if (target.hurtServer(level, source, damage)) {
            // LivingEntity.knockback is horizontal; the technique must also work when aimed up or down.
            val resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
            val force =
                direction
                    .scale(KNOCKBACK)
                    .add(0.0, LIFT, 0.0)
                    .scale(strength * (1 - resistance))
            TechniqueCombat.push(target, force)
        }
    }
}
