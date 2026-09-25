package io.github.romeoahmed.cursedoath.client;

import static com.google.common.base.Preconditions.checkState;
import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.combat.CursedEnergy;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CombatClientGameTest implements FabricClientGameTest {
    private static final int MELEE_APPROACH_TICKS = 20;
    private static final float MELEE_DAMAGE = 4, TARGET_HEALTH = 500;
    private static final double TARGET_X = 0.5, GROUND_Y = -60.0, TARGET_Z = 5.5;

    private record Cursor(double x, double y) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
            world.getServer().runCommand("execute as @a run cursedoath practice");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitFor(client ->
                    CombatInput.snapshot() != null && CombatInput.snapshot().enabled());
            verifyWheel(context, world);
            verifyMeleePreparation(context, world);
            verifyEmptySwing(context, world);
            verifyInfinity(context, world);
            context.getInput().lookAt(0f, 0f);
            context.getInput().pressKey(CombatInput.SELECT);
            context.runOnClient(client -> checkState(CombatInput.selected() == Technique.RED));
            var target = spawnTarget(world);
            world.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
            verifyCancellation(context, world, target);
            verifyRelease(context, world, target);
        }
        context.runOnClient(client -> {
            checkState(CombatInput.snapshot() == null, "Disconnect must clear the HUD state");
            checkState(CombatInput.selected() == Technique.BLUE, "Disconnect must reset technique selection");
        });
    }

    private static void verifyWheel(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().pressKey(CombatInput.SELECT);
        context.runOnClient(client -> checkState(CombatInput.selected() == Technique.RED));
        context.getInput().pressKey(CombatInput.WHEEL);
        context.waitFor(client -> client.gui.screen() instanceof TechniqueWheelScreen);
        capture(context, "technique-wheel");
        context.runOnClient(
                client -> checkState(!requireNonNull(client.gui.screen()).isPauseScreen()));
        // Exercise the dead zone through native input, without calling the screen's picker.
        var center = context.computeOnClient(client -> new Cursor(
                client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0));
        context.getInput().setCursorPos(center.x(), center.y());
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            checkState(client.gui.screen() instanceof TechniqueWheelScreen);
            checkState(requireNonNull(CombatInput.snapshot()).preparing() == 0);
        });
        pointAtBlue(context);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.runOnClient(client -> checkState(client.gui.screen() instanceof TechniqueWheelScreen));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> client.gui.screen() == null && CombatInput.selected() == Technique.BLUE);
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            var state = requireNonNull(CombatInput.snapshot());
            checkState(
                    state.preparing() == 0 && state.energy() == CursedEnergy.CAPACITY, "Left click must only select");
        });
        context.getInput().pressKey(CombatInput.WHEEL);
        context.waitFor(client -> client.gui.screen() instanceof TechniqueWheelScreen);
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(client -> client.gui.screen() == null && CombatInput.selected() == Technique.RED);
        context.getInput().pressKey(CombatInput.WHEEL);
        context.waitFor(client -> client.gui.screen() instanceof TechniqueWheelScreen);
        pointAtBlue(context);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client -> client.gui.screen() == null
                && CombatInput.snapshot() != null
                && CombatInput.snapshot().preparing() == Technique.BLUE.wireId());
        context.getInput().pressKey(CombatInput.CANCEL);
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == 0);
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
    }

    private static void pointAtBlue(ClientGameTestContext context) {
        var cursor = context.computeOnClient(client -> {
            var wheel = (TechniqueWheelScreen) requireNonNull(client.gui.screen());
            var window = client.getWindow();
            var choices = wheel.children().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.getMessage()
                            .equals(Component.translatable("wheel.cursed-oath." + Technique.BLUE.path())))
                    .toList();
            checkState(choices.size() == 1, "Blue must have one accessible choice");
            var blue = choices.getFirst();
            return new Cursor(
                    (blue.getX() + blue.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (blue.getY() + blue.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        });
        context.getInput().setCursorPos(cursor.x(), cursor.y());
    }

    private static void verifyMeleePreparation(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().pressKey(CombatInput.PULSE);
        world.getConnection().waitForServerboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().pulseReady());
        capture(context, "melee-prepared");
        context.waitTicks(MELEE_APPROACH_TICKS);
        context.runOnClient(client -> checkState(
                requireNonNull(CombatInput.snapshot()).pulseReady(), "Preparation must allow time to approach"));
        context.getInput().pressKey(CombatInput.CANCEL);
        context.waitFor(client ->
                CombatInput.snapshot() != null && !CombatInput.snapshot().pulseReady());
        capture(context, "melee-cancelled");
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
    }

    private static void verifyEmptySwing(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().lookAt(0f, -90f);
        context.getInput().pressKey(CombatInput.PULSE);
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().pulseReady());
        context.getInput().pressKey(options -> options.keyAttack);
        world.getConnection().waitForServerboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && !CombatInput.snapshot().pulseReady());
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
    }

    private static void verifyInfinity(ClientGameTestContext context, TestSingleplayerContext world) {
        for (int index = 0; index < Technique.values().length; index++)
            if (context.computeOnClient(client -> CombatInput.selected() != Technique.INFINITY))
                context.getInput().pressKey(CombatInput.SELECT);
        context.getInput().pressKey(CombatInput.CAST);
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().infinity());
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var level = world.getConnection().getServerLevel();
            var attacker = requireNonNull(EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND));
            var before = player.getHealth();
            player.hurtServer(level, level.damageSources().mobAttack(attacker), MELEE_DAMAGE);
            checkState(player.getHealth() == before, "Active Infinity must reject native contact damage");
            player.hurtServer(level, level.damageSources().generic(), MELEE_DAMAGE);
            checkState(player.getHealth() < before, "Infinity must not become universal damage immunity");
            player.heal(player.getMaxHealth());
        });
        context.getInput().pressKey(CombatInput.CANCEL);
        context.waitFor(client ->
                CombatInput.snapshot() != null && !CombatInput.snapshot().infinity());
        context.getInput().pressKey(CombatInput.SELECT);
        context.runOnClient(client -> checkState(CombatInput.selected() == Technique.BLUE));
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
    }

    private static void verifyCancellation(ClientGameTestContext context, TestSingleplayerContext world, UUID target) {
        context.getInput().pressKey(CombatInput.CAST);
        world.getConnection().waitForServerboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == Technique.RED.wireId());
        context.getInput().pressKey(CombatInput.CANCEL);
        world.getConnection().waitForServerboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == 0);
        context.runOnClient(client -> {
            var state = requireNonNull(CombatInput.snapshot());
            checkState(state.reserved() == 0, "Cancellation must release the reservation");
            checkState(state.energy()
                    == CursedEnergy.CAPACITY - Technique.RED.cost().startup());
            checkState(state.recovery() > 0, "Cancellation must retain recovery");
        });
        context.waitTicks(Technique.RED.preparation());
        world.getServer().runOnServer(server -> {
            var entity = target(world, target);
            checkState(entity.getHealth() == entity.getMaxHealth(), "A cancelled cast must not deal delayed damage");
        });
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().recovery() == 0);
    }

    private static void verifyRelease(ClientGameTestContext context, TestSingleplayerContext world, UUID target) {
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().energy() == CursedEnergy.CAPACITY);
        context.getInput().pressKey(CombatInput.CAST);
        world.getConnection().waitForServerboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == Technique.RED.wireId());
        capture(context, "red-preparation");
        world.getServer()
                .waitFor(server -> target(world, target).getHealth()
                        < target(world, target).getMaxHealth());
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().preparing() == 0);
        context.runOnClient(client -> {
            var state = requireNonNull(CombatInput.snapshot());
            checkState(state.reserved() == 0);
            var expected = CursedEnergy.CAPACITY
                    - Technique.RED.cost().startup()
                    - Technique.RED.cost().release();
            checkState(state.energy() == expected, "Red must leave %s energy, got %s", expected, state.energy());
        });
        capture(context, "red-impact");
        world.getServer().waitFor(server -> target(world, target).getZ() > TARGET_Z + 1);
        world.getConnection().waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
        var entityId = world.getServer()
                .computeOnServer(server -> target(world, target).getId());
        context.waitFor(client -> {
            var entity = client.level == null ? null : client.level.getEntity(entityId);
            return entity != null && entity.getZ() > TARGET_Z + 1;
        });
        capture(context, "red-displacement");
    }

    private static UUID spawnTarget(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var level = world.getConnection().getServerLevel();
            var target = requireNonNull(EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND));
            target.setPos(TARGET_X, GROUND_Y, TARGET_Z);
            // NoAI disables native travel as well as decisions; zero walking speed preserves physics.
            requireNonNull(target.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.0);
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
