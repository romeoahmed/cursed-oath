package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.projectile.Projectile

internal object InfinityDefense {
    private const val RADIUS = 2.5
    private const val MIN_SPEED_SQUARED = 0.0001
    private const val DEFLECTION_SPEED = 0.15
    private const val PARTICLE_SPREAD = 0.03
    private const val PARTICLE_SPEED = 0.01

    fun blocks(
        player: ServerPlayer,
        source: DamageSource,
    ): Boolean {
        if (!CombatRuntime.hasInfinity(player)) return false
        // Only declared contact sources; unknown and environmental sources remain unaffected.
        return source.`is`(DamageTypes.PLAYER_ATTACK) || source.`is`(DamageTypes.MOB_ATTACK) ||
            source.`is`(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.`is`(DamageTypes.ARROW) ||
            source.`is`(DamageTypes.TRIDENT)
    }

    fun interceptProjectiles(player: ServerPlayer) {
        val level = player.level()
        for (projectile in level.getEntitiesOfClass(Projectile::class.java, player.boundingBox.inflate(RADIUS))) {
            val incoming = projectile.position().subtract(player.boundingBox.center)
            val moving = projectile.deltaMovement.lengthSqr() >= MIN_SPEED_SQUARED
            if (projectile.owner !== player && moving && projectile.deltaMovement.dot(incoming) < 0.0) {
                projectile.deltaMovement = incoming.normalize().scale(DEFLECTION_SPEED)
                projectile.syncVelocity = true
                level.sendParticles(
                    ParticleTypes.END_ROD,
                    projectile.x,
                    projectile.y,
                    projectile.z,
                    2,
                    PARTICLE_SPREAD,
                    PARTICLE_SPREAD,
                    PARTICLE_SPREAD,
                    PARTICLE_SPEED,
                )
            }
        }
    }
}
