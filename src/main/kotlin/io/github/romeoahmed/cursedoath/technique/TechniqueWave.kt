package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.SweptVolume
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-swept volume; native entity tracking supplies spawn, interpolation and removal. */
class TechniqueWave(
    type: EntityType<out TechniqueWave>,
    level: Level,
) : TechniqueProjectile(type, level) {
    private var excavation: TerrainDestruction.Work? = null
    private var purple: PurpleFlight? = null
    private var direction = Vec3.ZERO
    private var origin = Vec3.ZERO
    private var next: Vec3? = null
    private var traveled = 0.0
    private val hit = HashSet<UUID>()
    private val size = Vec3(TechniqueTuning.DISMANTLE_WIDTH, 0.18, 0.18)

    override fun onRemoval(reason: RemovalReason) {
        purple?.finish() ?: excavation?.close()
        super.onRemoval(reason)
    }

    internal fun configure(
        player: ServerPlayer,
        ability: Technique,
        work: TerrainDestruction.Work,
    ) {
        launch(player, ability)
        if (ability == Technique.PURPLE) {
            purple = PurpleFlight(this, player, work)
            return
        }
        direction = player.lookAngle
        origin = position()
        excavation = work
        work.persistent = true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val flight = purple
        if (flight != null) flight.tick() else tickSlash()
    }

    private fun tickSlash() {
        val level = level() as? ServerLevel ?: return
        val work = excavation
        val player = work?.owner?.takeIf { it.isAlive && !it.isRemoved && !it.isSpectator }
        if (player == null || work.finished) {
            discard()
            return
        }
        if (player.level() !== level || tickCount >= MAX_AGE) {
            discard()
            return
        }
        val end = next ?: position().add(direction.scale(CONTACT_STEP))
        val bounds = SweptVolume(origin, end, size).bounds.inflate(MOVEMENT_MARGIN)
        if (!level.hasLoadedChunks(bounds)) {
            discard()
            return
        }
        if (work.ready) {
            advance(level, player, work)
        } else {
            val currentEnd = position().add(direction.scale(CONTACT_STEP))
            damage(level, player, SweptVolume(position(), currentEnd, size))
        }
    }

    private fun advance(
        level: ServerLevel,
        player: ServerPlayer,
        work: TerrainDestruction.Work,
    ) {
        val destination = next
        if (destination != null) {
            val volume = SweptVolume(position(), destination, size)
            damage(level, player, volume)
            val obstruction =
                level.clipIncludingBorder(
                    ClipContext(position(), destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this),
                )
            if (obstruction.type != HitResult.Type.MISS) {
                setPos(obstruction.location)
                discard()
                return
            }
            traveled += destination.distanceTo(position())
            setPos(destination)
            next = null
        }
        schedule(level, work)
    }

    private fun schedule(
        level: ServerLevel,
        work: TerrainDestruction.Work,
    ) {
        if (traveled >= SLASH_RANGE) {
            discard()
        } else {
            val end = position().add(direction.scale(minOf(SLASH_SPEED, SLASH_RANGE - traveled)))
            if (!level.isPositionEntityTicking(BlockPos.containing(end)) ||
                !level.hasLoadedChunks(SweptVolume(origin, end, size).bounds)
            ) {
                discard()
                return
            }
            next = end
            work.cuts(listOf(SweptVolume(position(), end, size)), origin)
        }
    }

    private fun damage(
        level: ServerLevel,
        player: ServerPlayer,
        volume: SweptVolume,
    ) {
        // Broad phase includes ordinary target movement; the narrow phase uses a relative segment.
        val bounds = volume.bounds.inflate(MOVEMENT_MARGIN)
        for (target in level.getEntitiesOfClass(LivingEntity::class.java, bounds)) {
            hitTarget(player, target, volume)
        }
    }

    private fun hitTarget(
        player: ServerPlayer,
        target: LivingEntity,
        volume: SweptVolume,
    ) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target) ||
            target.uuid in hit
        ) {
            return
        }
        val movement = target.position().subtract(Vec3(target.xo, target.yo, target.zo))
        val contact = volume.contact(target.boundingBox, movement) ?: return
        val level = player.level()
        val covered =
            level.clipIncludingBorder(
                ClipContext(
                    volume.source(contact, origin),
                    contact,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    this,
                ),
            )
        if (covered.type != HitResult.Type.MISS) return
        hit.add(target.uuid)
        target.hurtServer(level, level.damageSources().playerAttack(player), SLASH_DAMAGE)
    }

    companion object {
        private const val SLASH_RANGE = TechniqueTuning.DISMANTLE_RANGE
        private const val SLASH_SPEED = 6.0
        private const val SLASH_DAMAGE = TechniqueTuning.DISMANTLE_DAMAGE
        private const val MOVEMENT_MARGIN = 4.0
        private const val CONTACT_STEP = 0.001
        private const val MAX_AGE = 480
    }
}
