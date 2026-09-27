package io.github.romeoahmed.cursedoath.client.render.limitless;

import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;

/// Cosmetic fragments sample actual terrain; they never create falling-block entities.
public final class BlueDebris {
    private static final int MAX_FIELDS = 8, SAMPLES = 2;
    private static final Set<TechniqueOrb> ORBS = new LinkedHashSet<>();
    private static final double SPEED = 0.7;
    private static final int LIFETIME = 8;
    private static final float SCALE = 1.8f;
    private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;

    private BlueDebris() {}

    public static void initialize() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TechniqueOrb orb) ORBS.add(orb);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TechniqueOrb orb) ORBS.remove(orb);
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ORBS.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ORBS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(BlueDebris::tick);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        int remaining = MAX_FIELDS;
        for (var orb : ORBS)
            if (orb.technique() == Technique.BLUE && emit(client, orb.position()) && --remaining == 0) break;
    }

    private static boolean emit(Minecraft client, Vec3 center) {
        var level = client.level;
        var player = client.player;
        if (level == null
                || player == null
                || client.options.particles().get() != ParticleStatus.ALL
                || player.distanceToSqr(center) > MAX_DISTANCE_SQUARED) return false;
        for (int i = 0; i < SAMPLES; i++) {
            var random = level.getRandom();
            var offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize()
                    .scale(TechniqueTuning.BLUE_EXCAVATION);
            var position = center.add(offset);
            var state = level.getBlockState(BlockPos.containing(position));
            if (!state.isAir()
                    && !state.hasBlockEntity()
                    && state.getFluidState().isEmpty()) {
                var particle = client.particleEngine.createParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        position.x,
                        position.y,
                        position.z,
                        0,
                        0,
                        0);
                if (particle == null) continue;
                var velocity = offset.normalize().scale(-SPEED);
                particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
                particle.setLifetime(LIFETIME);
                particle.scale(SCALE);
            }
        }
        return true;
    }
}
