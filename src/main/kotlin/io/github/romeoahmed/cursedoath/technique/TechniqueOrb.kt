package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.domain.DomainInteractions
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Blue carries an attraction field; contact or range expiry settles Blue and detonates Red. */
class TechniqueOrb(
    type: EntityType<out TechniqueOrb>,
    level: Level,
) : TechniqueProjectile(type, level) {
    private var excavation: TerrainDestruction.Work? = null
    private var remaining = 0.0
    private var settled = false

    internal fun configure(
        player: ServerPlayer,
        ability: Technique,
        work: TerrainDestruction.Work,
    ) {
        launch(player, ability)
        excavation = work
        remaining = if (ability == Technique.BLUE) BLUE_RANGE else RED_RANGE
        deltaMovement = player.lookAngle.scale(if (ability == Technique.BLUE) BLUE_SPEED else RED_SPEED)
    }

    override fun onRemoval(reason: RemovalReason) {
        excavation?.close()
        super.onRemoval(reason)
    }

    override fun tick() {
        super.tick()
        val level = level() as? ServerLevel ?: return
        val work = excavation
        val player = work?.owner?.takeIf { it.isAlive && !it.isRemoved && !it.isSpectator }
        if (player == null || player.level() !== level || tickCount > TechniqueTuning.BLUE_DURATION) {
            discard()
            return
        }
        if (!settled && work.finished) {
            discard()
            return
        }
        if (!settled) travel(level, player, work)
        if (!isRemoved && technique == Technique.BLUE) BlueField.tick(player, position(), tickCount)
    }

    private fun travel(
        level: ServerLevel,
        player: ServerPlayer,
        work: TerrainDestruction.Work,
    ) {
        val movement = deltaMovement
        val distance = minOf(movement.length(), remaining)
        val direction = movement.normalize()
        val end = position().add(direction.scale(distance))
        if (!level.isPositionEntityTicking(BlockPos.containing(end)) ||
            !level.hasLoadedChunks(AABB(position(), end).inflate(AIM_MARGIN.toDouble()))
        ) {
            discard()
            return
        }
        val block =
            level.clipIncludingBorder(
                ClipContext(position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this),
            )
        val entity =
            ProjectileUtil
                .getManyEntityHitResult(
                    level,
                    this,
                    position(),
                    block.location,
                    AABB(position(), block.location).inflate(AIM_MARGIN.toDouble()),
                    { it is LivingEntity && TechniqueCombat.canAffect(player, it) },
                    AIM_MARGIN,
                    ClipContext.Block.COLLIDER,
                    true,
                    true,
                ).minByOrNull { it.location.distanceToSqr(position()) }
        val hit = entity ?: block
        val barrier = DomainInteractions.contact(player, position(), hit.location)
        if (barrier != null) {
            val power = if (technique == Technique.BLUE) TechniqueTuning.BLUE_OUTPUT else TechniqueTuning.RED_DAMAGE
            barrier.domain.damageShell(power, !barrier.domain.contains(position()))
            setPos(barrier.point.subtract(direction.scale(SURFACE_OFFSET)))
            impact(player, work, direction)
            return
        }
        val center =
            if (hit.type == HitResult.Type.BLOCK) {
                hit.location.subtract(direction.scale(SURFACE_OFFSET))
            } else {
                hit.location
            }
        setPos(center)
        remaining -= distance
        if (hit.type != HitResult.Type.MISS || remaining <= 0) impact(player, work, direction)
    }

    private fun impact(
        player: ServerPlayer,
        work: TerrainDestruction.Work,
        direction: Vec3,
    ) {
        settled = true
        deltaMovement = Vec3.ZERO
        if (technique == Technique.BLUE) {
            work.sphere(position(), TechniqueTuning.BLUE_EXCAVATION)
        } else {
            RedBlast.impact(player, position(), direction)
            work.sphere(position(), TechniqueTuning.RED_EXCAVATION)
            TechniqueCombat.event(
                player,
                TechniqueEvent(
                    level().dimension().identifier(),
                    uuid,
                    player.id,
                    technique.wireId,
                    TechniqueEvent.IMPACT,
                    level().gameTime,
                    position().subtract(direction),
                    position(),
                ),
            )
            level().playSound(
                null,
                x,
                y,
                z,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                IMPACT_VOLUME,
                IMPACT_PITCH,
            )
            excavation = null // Keep queued excavation alive after the projectile is discarded.
            discard()
        }
    }

    private companion object {
        const val BLUE_RANGE = 18.0
        const val RED_RANGE = 24.0
        const val BLUE_SPEED = 0.9
        const val RED_SPEED = 1.5
        const val AIM_MARGIN = 0.35f
        const val SURFACE_OFFSET = 0.01
        const val IMPACT_VOLUME = 2f
        const val IMPACT_PITCH = 0.65f
    }
}
