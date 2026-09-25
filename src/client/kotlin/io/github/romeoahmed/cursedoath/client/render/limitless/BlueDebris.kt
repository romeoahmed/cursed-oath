package io.github.romeoahmed.cursedoath.client.render.limitless

import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ParticleStatus
import net.minecraft.world.phys.Vec3

/** Cosmetic fragments sample the actual terrain; they never create falling-block entities. */
internal object BlueDebris {
    private const val SAMPLES = 2
    private const val SPEED = 0.7
    private const val LIFETIME = 8
    private const val SCALE = 1.8f
    private const val MAX_DISTANCE_SQUARED = 64.0 * 64.0

    fun emit(
        client: Minecraft,
        center: Vec3,
    ): Boolean {
        val level = client.level ?: return false
        val player = client.player ?: return false
        if (client.options.particles().get() != ParticleStatus.ALL ||
            player.distanceToSqr(center) > MAX_DISTANCE_SQUARED
        ) {
            return false
        }
        repeat(SAMPLES) {
            val random = level.random
            val offset =
                Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize()
                    .scale(TechniqueTuning.BLUE_EXCAVATION)
            val position = center.add(offset)
            val state = level.getBlockState(BlockPos.containing(position))
            if (!state.isAir && !state.hasBlockEntity() && state.fluidState.isEmpty) {
                val particle =
                    client.particleEngine.createParticle(
                        BlockParticleOption(ParticleTypes.BLOCK, state),
                        position.x,
                        position.y,
                        position.z,
                        0.0,
                        0.0,
                        0.0,
                    ) ?: return@repeat
                val velocity = offset.normalize().scale(-SPEED)
                particle.setParticleSpeed(velocity.x, velocity.y, velocity.z)
                particle.lifetime = LIFETIME
                particle.scale(SCALE)
            }
        }
        return true
    }
}
