package io.github.romeoahmed.cursedoath.client;

import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DomainClientGameTest implements FabricClientGameTest {
    private static final int SLASH_FRAMES = 3, REVEAL_TICKS = 30, SETTLE_TICKS = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        // Pixel comparisons need fixed illumination and no drifting clouds.
        context.runOnClient(client -> client.options.cloudStatus().set(CloudStatus.OFF));
        try (var world = context.worldBuilder().create()) {
            for (var command : List.of(
                    "time set noon",
                    "gamerule advance_time false",
                    "weather clear",
                    "gamerule advance_weather false",
                    "gamemode creative @a",
                    "tp @a 0.5 -50 0.5 0 0",
                    "execute as @a run cursedoath practice")) world.getServer().runCommand(command);
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitFor(client ->
                    CombatInput.snapshot() != null && CombatInput.snapshot().enabled());
            context.getInput().pressKey(options -> options.keyToggleGui);
            verifyVoid(context, world);
            verifyShrine(context, world);
        }
    }

    private static void verifyVoid(ClientGameTestContext context, TestSingleplayerContext world) {
        context.waitTicks(SETTLE_TICKS);
        var baseline = capture(context, "void-before");
        context.runOnClient(client -> CombatInput.choose(Technique.UNLIMITED_VOID, true));
        context.waitFor(
                client -> Domains.inLevel(requireNonNull(client.level)).stream().anyMatch(domain -> domain.closed()));
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().burnout() == 0);
        context.waitTicks(REVEAL_TICKS);
        checkEffect(context, baseline, "void-interior", true);
        capture(context, "void-interior-full");
        view(context, world, "tp @a 0.5 -50 0.5 35 -10", "void-interior-turned");
        view(context, world, "tp @a 0.5 -50 0.5 180 0", "void-interior-back");
        view(context, world, "tp @a 5.5 -50 4.5 0 0", "void-interior-moved");
        view(context, world, "tp @a 0.5 -50 -35 0 0", "void-exterior");
        context.getInput().pressKey(CombatInput.CANCEL);
        context.waitFor(client -> Domains.inLevel(requireNonNull(client.level)).isEmpty());
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().burnout() > 0);
        world.getServer().runCommand("tp @a 0.5 -50 0.5 0 0");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.waitTicks(SETTLE_TICKS);
        checkEffect(context, baseline, "void-collapsed", false);
    }

    private static void verifyShrine(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> CombatInput.choose(Technique.MALEVOLENT_SHRINE, true));
        context.waitFor(
                client -> Domains.inLevel(requireNonNull(client.level)).stream().anyMatch(domain -> !domain.closed()));
        world.getServer()
                .runCommand("summon minecraft:armor_stand 9.5 -60 -6 {Invulnerable:1b,NoGravity:1b,ShowArms:1b}");
        world.getServer().runCommand("tp @a 0.5 -53 -33 0 0");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(REVEAL_TICKS);
        capture(context, "shrine-front");
        view(context, world, "tp @a -24 -51 -25 -58 4", "shrine-quarter");
        view(context, world, "tp @a 0.5 -53 -26 0 0", "shrine-mouth-detail");
        view(context, world, "tp @a -11 -53 -23 -43 -12", "shrine-eaves-below");
        view(context, world, "tp @a -11 -55 -24 -38 14", "shrine-ossuary-detail");
        view(context, world, "tp @a 0.5 -45 -25 0 11", "shrine-front-gable");
        view(context, world, "tp @a 0.5 -45 4 180 11", "shrine-rear-gable");
        world.getServer().runCommand("tp @a 0.5 -56 12 0 0");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(SETTLE_TICKS);
        for (int frame = 0; frame < SLASH_FRAMES; frame++) {
            capture(context, "shrine-cuts-" + frame);
            context.waitTicks(2);
        }
        context.runOnClient(client -> client.options.hideLightningFlash().set(true));
        context.waitTicks(SETTLE_TICKS);
        capture(context, "shrine-cuts-subdued");
        context.runOnClient(client -> client.options.hideLightningFlash().set(false));
        context.getInput().pressKey(CombatInput.CANCEL);
        context.waitFor(client -> Domains.inLevel(requireNonNull(client.level)).isEmpty());
        capture(context, "shrine-collapsed");
    }

    private static void view(
            ClientGameTestContext context, TestSingleplayerContext world, String command, String name) {
        world.getServer().runCommand(command);
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(SETTLE_TICKS);
        capture(context, name);
    }
}
