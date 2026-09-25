package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.domain.DomainBoundary
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.AbstractArrow

object InfinityDefense {
    private const val CONTACT_RANGE_SQUARED = 16.0
    private const val RADIUS = 12.0
    private const val SURFACE_MARGIN = 0.001
    private const val APPROACH = 0.5
    private const val STOP_DISTANCE = 1.1
    private const val SLOW_DISTANCE = 3.0
    private const val PARTICLE_INTERVAL = 4L
    private const val PARTICLE_SPREAD = 0.03
    private const val PARTICLE_SPEED = 0.01

    fun blocks(
        player: ServerPlayer,
        source: DamageSource,
    ): Boolean {
        if (!CombatRuntime.hasInfinity(player)) return false
        val attacker = source.entity as? ServerPlayer
        if (attacker != null && source.`is`(DamageTypes.PLAYER_ATTACK) && bypasses(player, attacker)) return false
        // Only declared contact sources; unknown and environmental sources remain unaffected.
        return source.`is`(DamageTypes.PLAYER_ATTACK) || source.`is`(DamageTypes.MOB_ATTACK) ||
            source.`is`(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.`is`(DamageTypes.ARROW) ||
            source.`is`(DamageTypes.TRIDENT)
    }

    private fun bypasses(
        player: ServerPlayer,
        attacker: ServerPlayer,
    ): Boolean =
        CombatRuntime.fighter(attacker).defense.amplification &&
            attacker.boundingBox.distanceToSqr(player.boundingBox.center) <= CONTACT_RANGE_SQUARED

    /** Runs before native arrow/trident movement, including paths starting beyond the local query. */
    fun interceptArrow(projectile: Projectile) {
        val level = projectile.level() as? ServerLevel ?: return
        if (projectile !is AbstractArrow) return
        for (fighter in CombatRuntime.fighters(level)) {
            if (CombatRuntime.hasInfinity(fighter.player)) slow(projectile, fighter.player)
        }
    }

    internal fun interceptProjectiles(player: ServerPlayer) {
        for (projectile in player.level().getEntitiesOfClass(
            Projectile::class.java,
            player.boundingBox.inflate(RADIUS),
        )) {
            if (projectile !is AbstractArrow) slow(projectile, player)
        }
    }

    private fun slow(
        projectile: Projectile,
        player: ServerPlayer,
    ) {
        val start = projectile.position()
        val incoming = start.subtract(player.boundingBox.center)
        val motion = projectile.deltaMovement
        if (projectile.owner === player || motion.lengthSqr() == 0.0 || motion.dot(incoming) >= 0) return
        val distance = incoming.length()
        val slowRadius = STOP_DISTANCE + SLOW_DISTANCE
        val scale =
            if (distance > slowRadius + SURFACE_MARGIN) {
                DomainBoundary.crossing(start, start.add(motion), player.boundingBox.center, slowRadius) ?: return
            } else {
                val permitted = (distance - STOP_DISTANCE).coerceAtLeast(0.0)
                minOf(permitted / SLOW_DISTANCE, permitted * APPROACH / motion.length())
            }
        if (scale >= 1.0) return
        projectile.deltaMovement = motion.scale(scale)
        projectile.syncVelocity = true
        if (distance <= slowRadius && player.level().gameTime % PARTICLE_INTERVAL == 0L) {
            player.level().sendParticles(
                ParticleTypes.END_ROD,
                projectile.x,
                projectile.y,
                projectile.z,
                1,
                PARTICLE_SPREAD,
                PARTICLE_SPREAD,
                PARTICLE_SPREAD,
                PARTICLE_SPEED,
            )
        }
    }
}
