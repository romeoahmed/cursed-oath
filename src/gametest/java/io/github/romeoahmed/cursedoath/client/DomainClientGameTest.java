package io.github.romeoahmed.cursedoath.client;

import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import com.mojang.authlib.GameProfile;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.domain.BarrierState;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DomainClientGameTest implements FabricClientGameTest {
    private static final int SLASH_FRAMES = 3, REVEAL_TICKS = 30, SETTLE_TICKS = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
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
            verifyBarriers(context, world);
            verifyClash(context, world);
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
        awaitVoidAge(context, 2);
        world.getServer().runCommand("tick freeze");
        try {
            var opening = capture(context, "void-opening-start");
            context.runOnClient(client -> client.options.hideLightningFlash().set(true));
            checkEffect(context, opening, "void-opening-start-subdued", true);
        } finally {
            context.runOnClient(client -> client.options.hideLightningFlash().set(false));
            world.getServer().runCommand("tick unfreeze");
        }
        awaitVoidAge(context, 9);
        world.getServer().runCommand("tick freeze");
        try {
            var streams = capture(context, "void-opening-streams");
            context.runOnClient(client -> client.options.hideLightningFlash().set(true));
            checkEffect(context, streams, "void-opening-streams-subdued", true);
        } finally {
            context.runOnClient(client -> client.options.hideLightningFlash().set(false));
            world.getServer().runCommand("tick unfreeze");
        }
        awaitVoidAge(context, 17);
        capture(context, "void-opening-reveal");
        awaitVoidAge(context, REVEAL_TICKS);
        checkEffect(context, baseline, "void-interior", true);
        capture(context, "void-interior-full");
        view(context, world, "tp @a 0.5 -50 0.5 35 -10", "void-interior-turned");
        view(context, world, "tp @a 0.5 -50 0.5 180 0", "void-interior-back");
        view(context, world, "tp @a 0.5 -50 0.5 0 -85", "void-interior-up");
        view(context, world, "tp @a 0.5 -50 0.5 0 85", "void-interior-down");
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

    private static void awaitVoidAge(ClientGameTestContext context, int age) {
        context.waitFor(client -> Domains.inLevel(requireNonNull(client.level)).stream()
                .anyMatch(domain -> domain.closed() && domain.level().getGameTime() - domain.started() >= age));
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

    private static void verifyBarriers(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0.5 -60 0.5 0 25");
        world.getConnection().waitForClientboundPackets();
        var camera = context.computeOnClient(client -> client.options.getCameraType());
        try {
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            // Sample the synchronized visual state independently of combat timing.
            for (int strength : new int[] {100, 35, -1}) {
                context.runOnClient(client -> BarrierState.update(requireNonNull(client.player), strength));
                context.waitTicks(2);
                capture(context, "barrier-" + strength);
            }
        } finally {
            context.runOnClient(client -> {
                BarrierState.update(requireNonNull(client.player), 0);
                client.options.setCameraType(camera);
            });
        }
    }

    private static void verifyClash(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("gamemode spectator @a");
        world.getServer().runCommand("tp @a 0.5 -40 -40 0 15");
        var owners = world.getServer().computeOnServer(server -> {
            var level = world.getConnection().getServerPlayer().level();
            var players = new java.util.ArrayList<ServerPlayer>();
            for (int i = 0; i < 2; i++) {
                var cookie =
                        CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "domain-test"), false);
                var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
                player.connection = new ServerGamePacketListenerImpl(
                        server, new Connection(PacketFlow.SERVERBOUND), player, cookie);
                player.setPos(i * 20, -60, 0);
                Domains.open(player, Technique.UNLIMITED_VOID);
                players.add(player);
            }
            return players;
        });
        try {
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(REVEAL_TICKS);
            capture(context, "domain-clash-exterior");
            view(context, world, "tp @a 10 -55 0 0 0", "domain-clash-interior");
        } finally {
            world.getServer().runOnServer(server -> {
                for (var owner : owners) {
                    var domain = Domains.ownedBy(owner);
                    if (domain != null) Domains.end(domain);
                    owner.discard();
                }
            });
        }
    }

    private static void view(
            ClientGameTestContext context, TestSingleplayerContext world, String command, String name) {
        world.getServer().runCommand(command);
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(SETTLE_TICKS);
        capture(context, name);
    }
}
