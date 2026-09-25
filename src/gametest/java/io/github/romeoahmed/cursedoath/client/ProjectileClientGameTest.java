package io.github.romeoahmed.cursedoath.client;

import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.NullMarked;

/// Renders spawned projectiles through native tracking, independently of casting input.
@NullMarked
public final class ProjectileClientGameTest implements FabricClientGameTest {
    private static final BlockPos CUT = new BlockPos(0, -44, 3);
    private static final double FLIGHT_Z = 9.0;
    private static final int DEBRIS_EXPIRY = 40, POSE_TICKS = 4, RELEASE_TICKS = 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 0.5 -45 0.5 0 0");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.getInput().lookAt(0f, 0f);
            for (var technique : List.of(Technique.BLUE, Technique.RED, Technique.DISMANTLE))
                flight(context, world, technique);
            cleave(context, world);
            poses(context);
        }
    }

    private static void flight(ClientGameTestContext context, TestSingleplayerContext world, Technique technique) {
        world.getServer()
                .runOnServer(server -> TechniqueCombat.release(
                        world.getConnection().getServerPlayer(), UUID.randomUUID(), technique, null));
        context.waitFor(client -> {
            if (client.level == null) return false;
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof TechniqueProjectile projectile
                        && projectile.technique() == technique
                        && projectile.getZ() > FLIGHT_Z) return true;
            return false;
        });
        world.getServer().runCommand("tick freeze");
        capture(context, technique.name().toLowerCase(Locale.ROOT) + "-tracked-flight");
        world.getServer().runCommand("tick unfreeze");
        context.waitFor(client -> {
            if (client.level == null) return false;
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof TechniqueProjectile) return false;
            return true;
        });
    }

    private static void cleave(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("fill -5 -49 3 5 -39 7 stone");
        world.getConnection().waitForChunksRender();
        var work = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var terrain = requireNonNull(TerrainDestruction.reserve(player));
            TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, terrain);
            return terrain;
        });
        world.getServer()
                .waitFor(server -> world.getConnection()
                        .getServerLevel()
                        .getBlockState(CUT)
                        .isAir());
        world.getConnection().waitForChunksRender();
        capture(context, "cleave-contact-lattice");
        world.getServer().waitFor(server -> work.finished());
        world.getServer().runCommand("tp @a 0.5 -45 -10.5 0 0");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        capture(context, "cleave-terrain-after");
        context.waitTicks(DEBRIS_EXPIRY);
        capture(context, "cleave-terrain-settled");
    }

    private static void poses(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> client.options.getCameraType());
        try {
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            for (var technique :
                    List.of(Technique.BLUE, Technique.RED, Technique.PURPLE, Technique.DISMANTLE, Technique.CLEAVE)) {
                context.runOnClient(
                        client -> CastingAnimation.start(requireNonNull(client.player), technique, false, 0f));
                context.waitTicks(POSE_TICKS);
                capture(context, technique.name().toLowerCase(Locale.ROOT) + "-preparation-pose");
                context.runOnClient(
                        client -> CastingAnimation.start(requireNonNull(client.player), technique, true, 0f));
                context.waitTicks(RELEASE_TICKS);
                capture(context, technique.name().toLowerCase(Locale.ROOT) + "-release-pose");
            }
        } finally {
            context.runOnClient(client -> {
                CastingAnimation.stop(requireNonNull(client.player));
                client.options.setCameraType(original);
            });
        }
    }
}
