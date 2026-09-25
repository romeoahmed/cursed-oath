package io.github.romeoahmed.cursedoath.client;

import static com.google.common.base.Preconditions.checkState;
import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.combat.CursedEnergy;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PurpleClientGameTest implements FabricClientGameTest {
    private static final BlockPos WALL_CENTER = new BlockPos(0, -49, 10);
    private static final int LIGHT_TRANSITION_TICKS = 20, DEBRIS_EXPIRY = 40, SEPARATION_TICKS = 10, CHARGE_TICKS = 38;
    private static final double AFTER_WALL_Z = 30.0,
            FLIGHT_Z = 9.0,
            TARGET_X = 0.5,
            TARGET_Y = -50.0,
            TARGET_Z = 17.5,
            MOVEMENT_THRESHOLD = 0.2;
    private static final float TARGET_HEALTH = 500;

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        try (var world = context.worldBuilder().create()) {
            setup(context, world);
            var target = spawnTarget(world);
            capture(context, "purple-arena-before");
            select(context, Technique.PURPLE);
            context.getInput().pressKey(CombatInput.CAST);
            world.getConnection().waitForServerboundPackets();
            context.waitFor(client ->
                    CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == Technique.PURPLE.wireId());
            context.waitTicks(SEPARATION_TICKS);
            world.getServer().runCommand("tick freeze");
            capture(context, "purple-separated-blue-red");
            world.getServer().runCommand("tick unfreeze");
            context.waitTicks(CHARGE_TICKS - SEPARATION_TICKS);
            world.getServer().runCommand("tick freeze");
            capture(context, "purple-fusion");
            world.getServer().runCommand("tick unfreeze");
            awaitFlight(context, FLIGHT_Z);
            world.getServer().runCommand("tick freeze");
            world.getConnection().waitForClientboundEntityUpdates(TechniqueProjectiles.WAVE);
            capture(context, "purple-flight");
            world.getServer().runCommand("tick unfreeze");
            world.getServer().waitFor(server -> target(world, target).getHealth() < TARGET_HEALTH);
            world.getServer()
                    .waitFor(server -> world.getConnection()
                            .getServerLevel()
                            .getBlockState(WALL_CENTER)
                            .isAir());
            world.getServer().runCommand("tick freeze");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            verifyImpact(context, world, target);
            world.getServer().runCommand("tick unfreeze");
            awaitFlight(context, AFTER_WALL_Z);
            world.getServer().runCommand("tick freeze");
            world.getConnection().waitForChunksRender();
            capture(context, "purple-breached-wall");
            captureFlight(context, world);
            world.getServer().runCommand("tick unfreeze");
            awaitWaveRemoval(context, world);
            context.waitTicks(DEBRIS_EXPIRY);
            capture(context, "purple-tunnel-settled");
            verifyBlue(context, world);
        }
    }

    private static void awaitFlight(ClientGameTestContext context, double z) {
        context.waitFor(client -> {
            if (client.level == null) return false;
            for (var wave : client.level.entitiesForRendering())
                if (wave.getType().equals(TechniqueProjectiles.WAVE) && wave.getZ() > z) return true;
            return false;
        });
    }

    private static void captureFlight(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 18.5 -43 24.5 facing 0.5 -49 38.5");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        capture(context, "purple-flight-side");
        world.getServer().runCommand("time set midnight");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(LIGHT_TRANSITION_TICKS);
        capture(context, "purple-flight-side-night");
        world.getServer().runCommand("time set day");
    }

    private static void awaitWaveRemoval(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer()
                .waitFor(server -> world.getConnection()
                        .getServerLevel()
                        .getEntities(TechniqueProjectiles.WAVE, wave -> true)
                        .isEmpty());
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> {
            if (client.level == null) return false;
            for (var entity : client.level.entitiesForRendering())
                if (entity.getType().equals(TechniqueProjectiles.WAVE)) return false;
            return true;
        });
    }

    private static void verifyImpact(ClientGameTestContext context, TestSingleplayerContext world, UUID target) {
        world.getServer().runOnServer(server -> {
            checkState(
                    world.getConnection()
                            .getServerLevel()
                            .getBlockState(WALL_CENTER)
                            .isAir(),
                    "Queued Purple excavation must open the wall");
            checkState(
                    target(world, target).getHealth() == TARGET_HEALTH - TechniqueTuning.PURPLE_DAMAGE,
                    "Purple must deal one committed hit");
        });
        context.runOnClient(client -> {
            var state = requireNonNull(CombatInput.snapshot());
            checkState(state.reserved() == 0);
            var spent =
                    Technique.PURPLE.cost().startup() + Technique.PURPLE.cost().release();
            checkState(state.energy() == CursedEnergy.CAPACITY - spent);
        });
    }

    private static void setup(ClientGameTestContext context, TestSingleplayerContext world) {
        for (var command : List.of(
                "tp @a 0.5 -50 0.5 0 0",
                "gamemode creative @a",
                "execute as @a run cursedoath practice",
                "fill -8 -56 9 8 -41 15 stone")) world.getServer().runCommand(command);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().enabled());
        context.getInput().lookAt(0f, 0f);
    }

    private static void verifyBlue(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getServer().runCommand("tp @a 0.5 -50 12.5 0 0");
        var id = spawnTarget(world);
        world.getConnection().waitForClientboundPackets();
        select(context, Technique.BLUE);
        context.getInput().pressKey(CombatInput.CAST);
        world.getConnection().waitForServerboundPackets();
        world.getServer().waitFor(server -> target(world, id).getHealth() < TARGET_HEALTH);
        world.getServer().waitFor(server -> target(world, id).getZ() < TARGET_Z - MOVEMENT_THRESHOLD);
        world.getServer().runCommand("tick freeze");
        world.getServer().runCommand("tp @a 0.5 -45 6.5 facing 0.5 -49 13.5");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        capture(context, "blue-pull-and-compression");
        world.getServer().runCommand("tick unfreeze");
    }

    private static void select(ClientGameTestContext context, Technique technique) {
        for (int index = 0; index < Technique.values().length; index++) {
            if (context.computeOnClient(client -> CombatInput.selected() == technique)) return;
            context.getInput().pressKey(CombatInput.SELECT);
        }
        throw new AssertionError("Technique selection did not reach " + technique);
    }

    private static UUID spawnTarget(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var level = world.getConnection().getServerLevel();
            var target = requireNonNull(EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND));
            target.setPos(TARGET_X, TARGET_Y, TARGET_Z);
            target.setNoGravity(true);
            requireNonNull(target.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0);
            requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(TARGET_HEALTH);
            target.setHealth(TARGET_HEALTH);
            level.addFreshEntity(target);
            return target.getUUID();
        });
    }

    private static LivingEntity target(TestSingleplayerContext world, UUID id) {
        return (LivingEntity)
                requireNonNull(world.getConnection().getServerLevel().getEntity(id));
    }
}
