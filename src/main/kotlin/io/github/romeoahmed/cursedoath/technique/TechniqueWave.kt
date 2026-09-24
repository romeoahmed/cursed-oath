package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.SweptVolume
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
    private var direction = Vec3.ZERO
    private var origin = Vec3.ZERO
    private var next: Vec3? = null
    private var traveled = 0.0
    private val hit = HashSet<UUID>()
    private val size: Vec3 get() = if (technique == Technique.PURPLE) PURPLE_SIZE else SLASH_SIZE

    override fun onRemoval(reason: RemovalReason) {
        excavation?.close()
        super.onRemoval(reason)
    }

    internal fun configure(
        player: ServerPlayer,
        ability: Technique,
        work: TerrainDestruction.Work,
    ) {
        launch(player, ability)
        direction = player.lookAngle
        origin = position()
        excavation = work
        work.persistent = true
        work.drops = ability != Technique.PURPLE
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            if (technique == Technique.PURPLE) trail()
            return
        }
        val level = level() as? ServerLevel ?: return
        val work = excavation
        val player = work?.owner?.takeIf { it.isAlive && !it.isRemoved && !it.isSpectator }
        if (player == null || work.finished) {
            finish()
            return
        }
        if (player.level() !== level || tickCount >= MAX_AGE) {
            finish()
            return
        }
        val end = next ?: position().add(direction.scale(CONTACT_STEP))
        val bounds = SweptVolume(origin, end, size, technique == Technique.PURPLE).bounds.inflate(MOVEMENT_MARGIN)
        if (!level.hasLoadedChunks(bounds)) {
            finish()
            return
        }
        if (work.ready) {
            advance(level, player, work)
        } else {
            val currentEnd = position().add(direction.scale(CONTACT_STEP))
            damage(level, player, SweptVolume(position(), currentEnd, size, technique == Technique.PURPLE))
        }
    }

    private fun advance(
        level: ServerLevel,
        player: ServerPlayer,
        work: TerrainDestruction.Work,
    ) {
        val destination = next
        if (destination != null) {
            val volume = SweptVolume(position(), destination, size, technique == Technique.PURPLE)
            damage(level, player, volume)
            val obstruction =
                level.clipIncludingBorder(
                    ClipContext(position(), destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this),
                )
            if (obstruction.type != HitResult.Type.MISS) {
                setPos(obstruction.location)
                finish()
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
        val range = if (technique == Technique.PURPLE) PURPLE_RANGE else SLASH_RANGE
        if (traveled >= range) {
            finish()
        } else {
            val speed = if (technique == Technique.PURPLE) PURPLE_SPEED else SLASH_SPEED
            val end = position().add(direction.scale(minOf(speed, range - traveled)))
            if (!level.hasLoadedChunks(SweptVolume(origin, end, size, technique == Technique.PURPLE).bounds)) {
                finish()
                return
            }
            next = end
            work.sweep(SweptVolume(position(), end, size, technique == Technique.PURPLE), origin)
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
        val damage = if (technique == Technique.PURPLE) PURPLE_DAMAGE else SLASH_DAMAGE
        target.hurtServer(level, level.damageSources().playerAttack(player), damage)
    }

    private fun trail() {
        repeat(TRAIL_PARTICLES) {
            val offset =
                Vec3(
                    random.nextGaussian(),
                    random.nextGaussian(),
                    random.nextGaussian(),
                ).normalize().scale(TRAIL_RADIUS)
            level().addParticle(PURPLE_DUST, x + offset.x, y + offset.y, z + offset.z, 0.0, 0.0, 0.0)
        }
    }

    private fun finish() {
        excavation?.close()
        val level = level() as? ServerLevel
        if (level != null && technique == Technique.PURPLE) {
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, IMPACT_PITCH)
        }
        discard()
    }

    companion object {
        private val PURPLE_DUST = DustParticleOptions(Technique.PURPLE.color, 1f)
        private const val TRAIL_PARTICLES = 4
        private const val TRAIL_RADIUS = TechniqueTuning.PURPLE_RADIUS
        private val PURPLE_SIZE =
            Vec3(TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS)
        private val SLASH_SIZE = Vec3(TechniqueTuning.DISMANTLE_WIDTH, 0.18, 0.18)
        private const val PURPLE_RANGE = TechniqueTuning.PURPLE_RANGE
        private const val SLASH_RANGE = TechniqueTuning.DISMANTLE_RANGE
        private const val PURPLE_SPEED = 3.0
        private const val SLASH_SPEED = 6.0
        private const val PURPLE_DAMAGE = TechniqueTuning.PURPLE_DAMAGE
        private const val SLASH_DAMAGE = TechniqueTuning.DISMANTLE_DAMAGE
        private const val MOVEMENT_MARGIN = 4.0
        private const val IMPACT_PITCH = 0.7f
        private const val CONTACT_STEP = 0.001
        private const val MAX_AGE = 480
    }
}
