package io.github.romeoahmed.cursedoath.client;

import static com.google.common.base.Preconditions.checkState;
import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation;
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NullMarked;

/// Direct event fixtures isolate rendering from input and networking.
@NullMarked
public final class RenderClientGameTest implements FabricClientGameTest {
    private static final int LABEL_PADDING = 4,
            COMPACT_WIDTH = 640,
            COMPACT_HEIGHT = 480,
            EFFECT_AGE = 3,
            CHARGE_SAMPLE = 3,
            EFFECT_EXPIRY = 20;
    private static final List<BlockPos> GLASS_SECTIONS = List.of(
            new BlockPos(-6, -55, 6), new BlockPos(-6, -44, 6),
            new BlockPos(6, -55, 6), new BlockPos(6, -44, 6));
    private static final float VERTICAL_PITCH = 90;
    private static final double VERTICAL_DISTANCE = 4, EFFECT_DISTANCE = 12, CHARGE_DISTANCE = 2, FUSION_DISTANCE = 5;
    private static final List<Integer> FUSION_AGES = List.of(0, 4, 8, 16, 24, 28, 31, 34, 38, 44, 49),
            RELEASE_AGES = List.of(0, 2, 7);

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @a 0.5 -50 0.5 0 0");
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("execute as @a run cursedoath practice");
            world.getConnection().waitForClientboundPackets();
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitFor(client ->
                    CombatInput.snapshot() != null && CombatInput.snapshot().enabled());
            captureLanguages(context);
            world.getServer().runCommand("gamemode spectator @a");
            world.getConnection().waitForClientboundPackets();
            captureEffects(context, world);
            captureTransparency(context, world);
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            world.getConnection().waitForClientboundPackets();
            captureFusionPose(context);
        }
    }

    private static void captureFusionPose(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> client.options.getCameraType());
        try {
            context.getInput().lookAt(0f, 0f);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            for (boolean release : List.of(false, true))
                for (int age : release ? RELEASE_AGES : FUSION_AGES) {
                    context.runOnClient(client ->
                            CastingAnimation.start(requireNonNull(client.player), Technique.PURPLE, release, age));
                    context.waitTick();
                    capture(context, "purple-pose-" + (release ? "release" : "fusion") + "-" + age);
                }
        } finally {
            context.runOnClient(client -> {
                CastingAnimation.stop(requireNonNull(client.player));
                client.options.setCameraType(original);
            });
        }
    }

    private static void captureLanguages(ClientGameTestContext context) {
        var original =
                context.computeOnClient(client -> client.getLanguageManager().getSelected());
        try {
            for (var language : List.of("en_us", "zh_cn", "ja_jp")) {
                changeLanguage(context, language);
                context.runOnClient(client ->
                        checkState(!I18n.get(Technique.BLUE.translationKey()).equals(Technique.BLUE.translationKey())));
                capture(context, "hud-" + language);
                context.runOnClient(client -> {
                    CombatInput.choose(Technique.MALEVOLENT_SHRINE);
                    client.gui.setScreen(new TechniqueWheelScreen());
                });
                verifyWheelLayout(context);
                capture(context, "wheel-" + language);
                context.getInput().resizeWindow(COMPACT_WIDTH, COMPACT_HEIGHT);
                context.waitTick();
                verifyWheelLayout(context);
                capture(context, "wheel-" + language + "-compact");
                prepareScreenshots(context);
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    CombatInput.choose(Technique.BLUE);
                });
            }
        } finally {
            changeLanguage(context, original);
        }
    }

    private static void verifyWheelLayout(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = (TechniqueWheelScreen) requireNonNull(client.gui.screen());
            var buttons = screen.children().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .toList();
            checkState(buttons.size() == Technique.values().length, "Every technique needs an accessible choice");
            for (var button : buttons) {
                checkState(button.getX() >= 0
                        && button.getY() >= 0
                        && button.getRight() <= screen.width
                        && button.getBottom() <= screen.height);
                checkState(
                        client.font.width(button.getMessage()) <= button.getWidth() - LABEL_PADDING,
                        "Short names must fit without scrolling");
                for (var other : buttons) {
                    if (other.equals(button)) continue;
                    checkState(
                            button.getRight() <= other.getX()
                                    || other.getRight() <= button.getX()
                                    || button.getBottom() <= other.getY()
                                    || other.getBottom() <= button.getY(),
                            "Wheel choices must not overlap");
                }
            }
        });
    }

    private static void changeLanguage(ClientGameTestContext context, String language) {
        CompletableFuture<?> reload = context.computeOnClient(client -> {
            if (client.getLanguageManager().getSelected().equals(language))
                return CompletableFuture.completedFuture(null);
            client.getLanguageManager().setSelected(language);
            client.options.languageCode = language;
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> reload.isDone());
        reload.join();
        context.waitFor(client -> client.gui.overlay() == null);
    }

    private static void captureEffects(ClientGameTestContext context, TestSingleplayerContext world) {
        var hidden = context.computeOnClient(client -> client.gui.hud.isHidden());
        try {
            if (!hidden) context.getInput().pressKey(options -> options.keyToggleGui);
            for (var technique : List.of(Technique.BLUE, Technique.RED, Technique.PURPLE))
                verifyEffect(
                        context,
                        world,
                        new Effect(
                                technique, TechniqueEvent.PREPARE, technique.preparation() / CHARGE_SAMPLE, 0, false),
                        true);
            for (int age : FUSION_AGES)
                verifyEffect(
                        context, world, new Effect(Technique.PURPLE, TechniqueEvent.PREPARE, age, 0, false), false);
            for (int age : List.of(24, 32, 40))
                verifyEffect(
                        context, world, new Effect(Technique.PURPLE, TechniqueEvent.PREPARE, age, 0, false), false, 60);
            for (var technique : List.of(Technique.HEAL, Technique.RED, Technique.CLEAVE))
                verifyEffect(context, world, new Effect(technique), true);
            verifyEffect(
                    context, world, new Effect(Technique.CLEAVE, TechniqueEvent.RELEASE, EFFECT_AGE, 0, true), true);
            verifyEffect(
                    context,
                    world,
                    new Effect(Technique.CLEAVE, TechniqueEvent.RELEASE, EFFECT_AGE, VERTICAL_PITCH, false),
                    true);
            verifyEffect(
                    context,
                    world,
                    new Effect(Technique.CLEAVE, TechniqueEvent.BLACK_FLASH, EFFECT_AGE, 0, false),
                    true);
        } finally {
            context.runOnClient(client -> {
                if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
            });
        }
    }

    private static void captureTransparency(ClientGameTestContext context, TestSingleplayerContext world) {
        boolean original = context.computeOnClient(
                client -> client.options.improvedTransparency().get());
        int fov = context.computeOnClient(client -> client.options.fov().get());
        world.getServer().runCommand("fill -6 -55 6 6 -44 6 lime_stained_glass");
        world.getServer().runCommand("fill -6 -55 9 6 -44 9 white_concrete");
        world.getServer().runCommand("tick freeze");
        try {
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.getInput().lookAt(0f, 0f);
            context.runOnClient(client -> client.options.fov().set(70));
            for (boolean improved : List.of(false, true)) {
                context.runOnClient(
                        client -> client.options.improvedTransparency().set(improved));
                // Changing OIT invalidates compiled terrain on the next extraction.
                context.waitTick();
                world.getConnection().waitForChunksRender();
                // In 26.3 an empty build queue can precede scheduling every visible section.
                context.waitFor(client -> GLASS_SECTIONS.stream()
                        .allMatch(pos -> client.levelRenderer.isSectionCompiledAndVisible(pos, 0)));
                var baseline = capture(context, "glass-background-" + improved);
                var event = context.computeOnClient(client -> {
                    var player = requireNonNull(client.player);
                    var level = requireNonNull(client.level);
                    var eye = player.getEyePosition();
                    return new TechniqueEvent(
                            level.dimension().identifier(),
                            UUID.randomUUID(),
                            -1,
                            Technique.BLUE.wireId(),
                            TechniqueEvent.PREPARE,
                            level.getGameTime() - 10,
                            eye,
                            eye.add(player.getLookAngle().scale(CHARGE_DISTANCE)));
                });
                context.runOnClient(client -> TechniqueVisuals.accept(event));
                try {
                    checkGlass(baseline, capture(context, "glass-glow-" + improved));
                } finally {
                    cancelEffect(context, event);
                }
            }
        } finally {
            context.runOnClient(client -> {
                client.options.improvedTransparency().set(original);
                client.options.fov().set(fov);
            });
            world.getServer().runCommand("tick unfreeze");
            world.getServer().runCommand("fill -6 -55 6 6 -44 6 air");
            world.getServer().runCommand("fill -6 -55 9 6 -44 9 air");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
        }
    }

    private static void checkGlass(Path baseline, Path actual) {
        try (var before = NativeImage.read(Files.readAllBytes(baseline));
                var after = NativeImage.read(Files.readAllBytes(actual))) {
            int samples = 0, missing = 0, body = 0;
            // This annulus lies outside the body but inside its glow at the fixture's fixed camera.
            int centerX = before.getWidth() / 2, centerY = before.getHeight() / 2;
            double inner = Math.pow(before.getHeight() * 0.25, 2), outer = Math.pow(before.getHeight() * 0.29, 2);
            int extent = (int) Math.ceil(before.getHeight() * 0.29);
            for (int y = Math.max(0, centerY - extent); y < Math.min(before.getHeight(), centerY + extent); y++)
                for (int x = Math.max(0, centerX - extent); x < Math.min(before.getWidth(), centerX + extent); x++) {
                    int radiusSquared = (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY);
                    if (radiusSquared >= 400 && (radiusSquared < inner || radiusSquared > outer)) continue;
                    int expected = before.getPixel(x, y), observed = after.getPixel(x, y);
                    if (radiusSquared < 400) {
                        if (Math.max(
                                        Math.abs(ARGB.red(observed) - ARGB.red(expected)),
                                        Math.max(
                                                Math.abs(ARGB.green(observed) - ARGB.green(expected)),
                                                Math.abs(ARGB.blue(observed) - ARGB.blue(expected))))
                                > 40) body++;
                        continue;
                    }
                    if (ARGB.green(expected) <= ARGB.red(expected) + 8
                            || ARGB.green(expected) <= ARGB.blue(expected) + 8) continue;
                    samples++;
                    // Blue glow adds negligible red here; a missing glass layer exposes the pale wall.
                    if (ARGB.red(observed) - ARGB.red(expected) > 20) missing++;
                }
            checkState(samples > 100, "The baseline must contain rendered glass; samples=%s", samples);
            checkState(body > 100, "The glass comparison must include a visible energy body; pixels=%s", body);
            checkState(
                    missing < samples / 100,
                    "Transparent glow must not erase the stained glass behind it; missing=%s/%s",
                    missing,
                    samples);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record Effect(Technique technique, int stage, int age, float pitch, boolean reverse) {
        Effect(Technique technique) {
            this(
                    technique,
                    technique == Technique.RED ? TechniqueEvent.IMPACT : TechniqueEvent.RELEASE,
                    EFFECT_AGE,
                    0,
                    false);
        }

        double distance() {
            if (stage == TechniqueEvent.PREPARE)
                return technique == Technique.PURPLE ? FUSION_DISTANCE : CHARGE_DISTANCE;
            return pitch == VERTICAL_PITCH ? VERTICAL_DISTANCE : EFFECT_DISTANCE;
        }
    }

    private static void verifyEffect(
            ClientGameTestContext context, TestSingleplayerContext world, Effect effect, boolean verifyExpiry) {
        verifyEffect(context, world, effect, verifyExpiry, 0);
    }

    private static void verifyEffect(
            ClientGameTestContext context,
            TestSingleplayerContext world,
            Effect effect,
            boolean verifyExpiry,
            int angle) {
        var technique = effect.technique();
        int stage = effect.stage(), age = effect.age();
        float pitch = effect.pitch();
        boolean reverse = effect.reverse();
        context.getInput().lookAt(0f, pitch);
        context.waitTick();
        var label = stage == TechniqueEvent.BLACK_FLASH
                ? "black-flash"
                : technique.name().toLowerCase(Locale.ROOT);
        var name = label + "-" + (int) pitch + "-" + (reverse ? "back" : "front") + "-" + stage + "-" + age
                + (angle == 0 ? "" : "-oblique");
        // Screenshot readback can tick the game; freeze the phase, not only the partial tick.
        world.getServer().runCommand("tick freeze");
        world.getConnection().waitForClientboundPackets();
        try {
            var baseline = capture(context, name + "-before");
            var event = context.computeOnClient(client -> {
                var player = requireNonNull(client.player);
                var level = requireNonNull(client.level);
                var direction = player.getLookAngle();
                var destination = player.getEyePosition().add(direction.scale(effect.distance()));
                // A successful contact may start inside the target; zero separation is not a miss.
                var origin = angle != 0
                        ? destination.subtract(direction.yRot((float) Math.toRadians(angle)))
                        : reverse
                                ? destination.add(direction)
                                : technique == Technique.CLEAVE && stage != TechniqueEvent.BLACK_FLASH && pitch == 0
                                        ? destination
                                        : player.getEyePosition();
                return new TechniqueEvent(
                        level.dimension().identifier(),
                        UUID.randomUUID(),
                        -1,
                        technique.wireId(),
                        stage,
                        level.getGameTime() - age,
                        origin,
                        destination);
            });
            context.runOnClient(client -> TechniqueVisuals.accept(event));
            try {
                if (technique == Technique.PURPLE && stage == TechniqueEvent.PREPARE && age == 31 && angle == 0)
                    checkFusionColors(capture(context, name));
                else checkEffect(context, baseline, name, true);
                if (verifyExpiry) {
                    int lifetime = stage == TechniqueEvent.PREPARE ? technique.preparation() : EFFECT_EXPIRY;
                    world.getServer().runCommand("tick unfreeze");
                    context.waitFor(client -> requireNonNull(client.level).getGameTime() - event.tick() > lifetime);
                    checkEffect(context, baseline, name + "-expired", false);
                }
            } finally {
                if (stage == TechniqueEvent.PREPARE) cancelEffect(context, event);
            }
        } finally {
            world.getServer().runCommand("tick unfreeze");
        }
    }

    private static void cancelEffect(ClientGameTestContext context, TechniqueEvent event) {
        context.runOnClient(client -> TechniqueVisuals.accept(new TechniqueEvent(
                event.dimension(),
                event.id(),
                event.actor(),
                event.technique(),
                TechniqueEvent.CANCEL,
                event.tick(),
                event.origin(),
                event.destination())));
    }

    private static void checkFusionColors(Path screenshot) {
        try (var image = NativeImage.read(Files.readAllBytes(screenshot))) {
            int blue = 0, red = 0, purple = 0;
            for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y++)
                for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                    int color = image.getPixel(x, y);
                    int r = ARGB.red(color), g = ARGB.green(color), b = ARGB.blue(color);
                    if (b > r + 70 && b > g + 10) blue++;
                    if (r > g + 60 && r > b + 40) red++;
                    if (r > g + 25 && b > g + 50 && b > r) purple++;
                }
            // Coexistence catches delayed recoloring without requiring exact GPU pixels.
            checkState(
                    blue > 16 && red > 16 && purple > 16,
                    "Partial overlap must show Purple while both source colors remain visible");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
