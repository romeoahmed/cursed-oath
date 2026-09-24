package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Attraction is advanced by its visible orb, using the same native entity clock. */
internal object BlueField {
    private const val RADIUS = TechniqueTuning.BLUE_RADIUS
    private const val MIN_DISTANCE = 0.5
    private const val PULL = 0.42
    private const val CORE_RADIUS = TechniqueTuning.BLUE_CORE
    private const val CORE_DAMAGE = TechniqueTuning.BLUE_DAMAGE
    private const val DAMAGE_INTERVAL = TechniqueTuning.BLUE_INTERVAL

    fun tick(
        owner: ServerPlayer,
        center: Vec3,
        age: Int,
    ) {
        val bounds = AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
        val crush = (age - 1) % DAMAGE_INTERVAL == 0
        for (target in owner.level().getEntitiesOfClass(LivingEntity::class.java, bounds)) {
            pull(owner, target, center, crush)
        }
    }

    private fun pull(
        owner: ServerPlayer,
        target: LivingEntity,
        center: Vec3,
        crush: Boolean,
    ) {
        if (!TechniqueCombat.canAffect(owner, target) || TechniqueCombat.hasInfinity(target)) return
        val delta = center.subtract(target.boundingBox.center)
        val distance = delta.length()
        if (distance > RADIUS || !owner.level().hasLoadedChunks(AABB(center, target.boundingBox.center))) return
        val obstruction =
            owner.level().clip(
                ClipContext(
                    center,
                    target.boundingBox.center,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner,
                ),
            )
        if (obstruction.type == HitResult.Type.MISS) {
            val resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
            if (distance >= MIN_DISTANCE) {
                TechniqueCombat.push(target, delta.scale(PULL * (1 - resistance) / distance))
            }
            if (crush && distance <= CORE_RADIUS) {
                target.hurtServer(owner.level(), owner.level().damageSources().playerAttack(owner), CORE_DAMAGE)
            }
        }
    }
}
