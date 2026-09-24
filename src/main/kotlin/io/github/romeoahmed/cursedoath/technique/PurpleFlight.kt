package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.world.SweptVolume
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import io.github.romeoahmed.cursedoath.world.hasLoadedChunks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Flight owns contact; queued terrain work never determines speed or occlusion. */
internal class PurpleFlight(
    private val projectile: TechniqueWave,
    private val caster: ServerPlayer,
    private val terrain: TerrainDestruction.Work,
) {
    private val level = projectile.level() as ServerLevel
    private val origin = projectile.position()
    private val direction = caster.lookAngle
    private val hit = hashSetOf(caster.uuid)
    private var traveled = 0.0

    fun tick() {
        val start = projectile.position()
        val distance = minOf(TechniqueTuning.PURPLE_SPEED, TechniqueTuning.PURPLE_RANGE - traveled)
        val end = start.add(direction.scale(distance))
        val volume = SweptVolume(start, end, SIZE, true)
        if (!level.isPositionEntityTicking(BlockPos.containing(end)) || !level.hasLoadedChunks(volume.bounds) ||
            !level.worldBorder.isWithinBounds(volume.bounds)
        ) {
            projectile.discard()
            return
        }
        for (target in level.getEntitiesOfClass(LivingEntity::class.java, volume.bounds.inflate(MOVEMENT_MARGIN))) {
            if (target.uuid in hit || !TechniqueCombat.canAffect(caster, target) ||
                TechniqueCombat.hasInfinity(target)
            ) {
                continue
            }
            val movement = target.position().subtract(Vec3(target.xo, target.yo, target.zo))
            if (volume.entry(target.boundingBox, movement) != null) {
                hit.add(target.uuid)
                target.hurtServer(level, level.damageSources().playerAttack(caster), TechniqueTuning.PURPLE_DAMAGE)
            }
        }
        if (!terrain.finished) {
            val previous = if (traveled == 0.0) null else SweptVolume(origin, start, SIZE, true)
            terrain.pierce(volume, previous)
        }
        traveled += distance
        projectile.setPos(end)
        if (traveled >= TechniqueTuning.PURPLE_RANGE) projectile.discard()
    }

    fun finish() = terrain.seal()

    private companion object {
        val SIZE = Vec3(TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS)
        const val MOVEMENT_MARGIN = 4.0
    }
}
