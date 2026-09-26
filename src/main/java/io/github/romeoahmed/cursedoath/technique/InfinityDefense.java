package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.domain.DomainBoundary;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

public final class InfinityDefense {
    private static final double CONTACT_RANGE_SQUARED = 16,
            RADIUS = 12,
            SURFACE_MARGIN = 0.001,
            APPROACH = 0.5,
            STOP_DISTANCE = 1.1,
            SLOW_DISTANCE = 3,
            PARTICLE_SPREAD = 0.03,
            PARTICLE_SPEED = 0.01;
    private static final long PARTICLE_INTERVAL = 4;

    private InfinityDefense() {}

    public static boolean blocks(ServerPlayer player, DamageSource source) {
        if (!CombatRuntime.hasInfinity(player)) return false;
        if (source.getEntity() instanceof ServerPlayer attacker
                && source.is(DamageTypes.PLAYER_ATTACK)
                && bypasses(player, attacker)) return false;
        // Unknown and environmental sources remain unaffected.
        return source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.ARROW)
                || source.is(DamageTypes.TRIDENT);
    }

    private static boolean bypasses(ServerPlayer player, ServerPlayer attacker) {
        return CombatRuntime.hasAmplification(attacker)
                && attacker.getBoundingBox()
                                .distanceToSqr(player.getBoundingBox().getCenter())
                        <= CONTACT_RANGE_SQUARED;
    }
    /// Slows incoming arrows and tridents before native movement, even outside the local scan radius.
    ///
    /// @param projectile native projectile; non-arrow types and client-side calls are ignored
    public static void interceptArrow(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || !(projectile instanceof AbstractArrow)) return;
        for (var fighter : CombatRuntime.fighters(level))
            if (CombatRuntime.hasInfinity(fighter.player())) slow(projectile, fighter.player());
    }

    public static void interceptProjectiles(ServerPlayer player) {
        for (var projectile : player.level()
                .getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(RADIUS)))
            if (!(projectile instanceof AbstractArrow)) slow(projectile, player);
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    private static void slow(Projectile projectile, ServerPlayer player) {
        var start = projectile.position();
        var incoming = start.subtract(player.getBoundingBox().getCenter());
        var motion = projectile.getDeltaMovement();
        if (projectile.getOwner() == player || motion.lengthSqr() == 0 || motion.dot(incoming) >= 0) return;
        double distance = incoming.length(), slowRadius = STOP_DISTANCE + SLOW_DISTANCE, scale;
        if (distance > slowRadius + SURFACE_MARGIN) {
            var crossing = DomainBoundary.crossing(
                    start, start.add(motion), player.getBoundingBox().getCenter(), slowRadius);
            if (crossing == null) return;
            scale = crossing;
        } else {
            double permitted = Math.max(0, distance - STOP_DISTANCE);
            scale = Math.min(permitted / SLOW_DISTANCE, permitted * APPROACH / motion.length());
        }
        if (scale >= 1) return;
        projectile.setDeltaMovement(motion.scale(scale));
        projectile.syncVelocity = true;
        if (distance <= slowRadius && player.level().getGameTime() % PARTICLE_INTERVAL == 0)
            player.level()
                    .sendParticles(
                            ParticleTypes.END_ROD,
                            projectile.getX(),
                            projectile.getY(),
                            projectile.getZ(),
                            1,
                            PARTICLE_SPREAD,
                            PARTICLE_SPREAD,
                            PARTICLE_SPREAD,
                            PARTICLE_SPEED);
    }
}
