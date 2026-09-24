package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.IdentityHashMap

internal object BlueFields {
    private const val LIFETIME = TechniqueTuning.BLUE_DURATION
    private const val RADIUS = TechniqueTuning.BLUE_RADIUS
    private const val MIN_DISTANCE = 0.5
    private const val PULL = 0.42
    private const val CORE_RADIUS = TechniqueTuning.BLUE_CORE
    private const val CORE_DAMAGE = TechniqueTuning.BLUE_DAMAGE
    private const val DAMAGE_INTERVAL = TechniqueTuning.BLUE_INTERVAL

    class Field(
        val owner: ServerPlayer,
        var center: Vec3,
        var remaining: Int = LIFETIME,
    ) {
        val level: ServerLevel = owner.level()
    }

    private val fields = IdentityHashMap<ServerLevel, MutableList<Field>>()

    fun create(
        player: ServerPlayer,
        center: Vec3,
    ): Field {
        val active = fields.getOrPut(player.level()) { mutableListOf() }
        active.removeAll { it.owner.uuid == player.uuid }
        return Field(player, center).also(active::add)
    }

    fun remove(field: Field) {
        fields[field.level]?.remove(field)
    }

    fun tick(level: ServerLevel) {
        val active = fields[level] ?: return
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val field = iterator.next()
            val owner = field.owner.takeIf { it.isAlive && !it.isRemoved && !it.isSpectator }
            if (field.remaining-- <= 0 || owner == null || owner.level() !== level) {
                iterator.remove()
            } else {
                val bounds = AABB.ofSize(field.center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
                for (target in level.getEntitiesOfClass(LivingEntity::class.java, bounds)) {
                    pull(owner, target, field.center, (LIFETIME - field.remaining) % DAMAGE_INTERVAL == 1)
                }
            }
        }
        if (active.isEmpty()) fields.remove(level)
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

    fun clear() = fields.clear()
}
