package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

object TechniqueCombat {
    private const val HEAL_AMOUNT = 6f
    private const val RELEASE_VOLUME = 0.8f
    private const val RELEASE_PITCH = 1.2f
    private const val BLACK_FLASH_VOLUME = 1.2f
    private const val BLACK_FLASH_PITCH = 0.65f
    private const val EVENT_RANGE = 96.0
    private const val MAX_SPEED = 5.0

    internal fun release(
        player: ServerPlayer,
        id: UUID,
        technique: Technique,
        terrain: TerrainDestruction.Work? = null,
    ) {
        val level = player.level()
        val work = if (technique.destroysTerrain) terrain ?: TerrainDestruction.reserve(player) ?: return else null
        val end = apply(player, technique, work)
        if (end == null) {
            event(player, id, technique, TechniqueEvent.CANCEL, player.eyePosition)
            return
        }
        event(player, id, technique, TechniqueEvent.RELEASE, end)
        val sound =
            when (technique) {
                Technique.BLUE -> SoundEvents.BEACON_ACTIVATE
                Technique.RED -> SoundEvents.FIRECHARGE_USE
                Technique.PURPLE -> SoundEvents.GENERIC_EXPLODE.value()
                Technique.HEAL -> SoundEvents.AMETHYST_BLOCK_RESONATE
                else -> SoundEvents.PLAYER_ATTACK_SWEEP
            }
        level.playSound(null, end.x, end.y, end.z, sound, SoundSource.PLAYERS, RELEASE_VOLUME, RELEASE_PITCH)
    }

    private fun apply(
        player: ServerPlayer,
        technique: Technique,
        work: TerrainDestruction.Work?,
    ): Vec3? =
        when (technique) {
            Technique.BLUE, Technique.RED, Technique.PURPLE, Technique.DISMANTLE -> {
                TechniqueProjectiles.release(player, technique, checkNotNull(work))
                player.eyePosition
            }

            Technique.CLEAVE -> {
                CleaveContact.release(player, checkNotNull(work))
            }

            Technique.HEAL -> {
                player.heal(HEAL_AMOUNT)
                player.position().add(0.0, 1.0, 0.0)
            }

            Technique.INFINITY -> {
                null
            }
        }

    fun blackFlash(
        player: ServerPlayer,
        target: LivingEntity,
    ) {
        event(player, UUID.randomUUID(), Technique.CLEAVE, TechniqueEvent.BLACK_FLASH, target.boundingBox.center)
        player.level().playSound(
            null,
            target.x,
            target.y,
            target.z,
            SoundEvents.PLAYER_ATTACK_CRIT,
            SoundSource.PLAYERS,
            BLACK_FLASH_VOLUME,
            BLACK_FLASH_PITCH,
        )
    }

    fun event(
        player: ServerPlayer,
        id: UUID,
        technique: Technique,
        stage: Int,
        end: Vec3,
    ) {
        val level = player.level()
        // Contact can coincide with the eyes; preserve its cutting direction even then.
        val origin =
            if (technique == Technique.CLEAVE && stage == TechniqueEvent.RELEASE) {
                end.subtract(player.lookAngle)
            } else {
                player.eyePosition
            }
        val payload =
            TechniqueEvent(
                level.dimension().identifier(),
                id,
                player.id,
                technique.wireId,
                stage,
                level.gameTime,
                origin,
                end,
            )
        event(player, payload)
    }

    internal fun event(
        player: ServerPlayer,
        payload: TechniqueEvent,
    ) {
        val level = player.level()
        for (viewer in PlayerLookup.around(level, payload.destination, EVENT_RANGE)) {
            if (ServerPlayNetworking.canSend(viewer, TechniqueEvent.TYPE)) {
                ServerPlayNetworking.send(viewer, payload)
            }
        }
    }

    fun canAffect(
        owner: ServerPlayer,
        target: LivingEntity,
    ): Boolean {
        if (!target.isAlive || target.isSpectator) return false
        if (owner === target || owner.isAlliedTo(target)) return false
        return target !is ServerPlayer || (!target.isCreative && owner.canHarmPlayer(target))
    }

    internal fun hasInfinity(target: LivingEntity): Boolean =
        target is ServerPlayer && CombatRuntime.hasInfinity(target)

    internal fun contact(
        player: ServerPlayer,
        range: Double,
    ): HitResult? {
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(range))
        val level = player.level()
        if (!level.hasLoadedChunks(AABB(start, end))) return null
        val block =
            level.clipIncludingBorder(
                ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player,
                ),
            )
        // Vanilla reports the actual surface, including a ray starting inside an entity.
        return ProjectileUtil
            .getManyEntityHitResult(
                level,
                player,
                start,
                block.location,
                AABB(start, block.location),
                { it is LivingEntity && canAffect(player, it) },
                0f,
                ClipContext.Block.COLLIDER,
                true,
                true,
            ).minByOrNull { it.location.distanceToSqr(start) } ?: block
    }

    internal fun push(
        target: LivingEntity,
        force: Vec3,
    ) {
        target.push(force)
        val velocity = target.deltaMovement
        if (velocity.lengthSqr() > MAX_SPEED * MAX_SPEED) target.deltaMovement = velocity.normalize().scale(MAX_SPEED)
        target.syncVelocity = true
    }
}
