package io.github.romeoahmed.cursedoath.client;

import static com.google.common.base.Preconditions.checkState;
import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation;
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
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
    private static final float VERTICAL_PITCH = 90;
    private static final double VERTICAL_DISTANCE = 4, EFFECT_DISTANCE = 12, CHARGE_DISTANCE = 2, FUSION_DISTANCE = 8;
    private static final List<Integer> FUSION_AGES = List.of(32, 36, 49), RELEASE_AGES = List.of(0, 2, 7);

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
            captureEffects(context);
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

    private static void captureEffects(ClientGameTestContext context) {
        var hidden = context.computeOnClient(client -> client.gui.hud.isHidden());
        try {
            if (!hidden) context.getInput().pressKey(options -> options.keyToggleGui);
            for (var technique : List.of(Technique.BLUE, Technique.RED, Technique.PURPLE))
                verifyEffect(
                        context,
                        new Effect(
                                technique, TechniqueEvent.PREPARE, technique.preparation() / CHARGE_SAMPLE, 0, false),
                        true);
            for (int age : FUSION_AGES)
                verifyEffect(context, new Effect(Technique.PURPLE, TechniqueEvent.PREPARE, age, 0, false), false);
            for (var technique : List.of(Technique.HEAL, Technique.RED, Technique.CLEAVE))
                verifyEffect(context, new Effect(technique), true);
            verifyEffect(context, new Effect(Technique.CLEAVE, TechniqueEvent.RELEASE, EFFECT_AGE, 0, true), true);
            verifyEffect(
                    context,
                    new Effect(Technique.CLEAVE, TechniqueEvent.RELEASE, EFFECT_AGE, VERTICAL_PITCH, false),
                    true);
            verifyEffect(context, new Effect(Technique.CLEAVE, TechniqueEvent.BLACK_FLASH, EFFECT_AGE, 0, false), true);
        } finally {
            context.runOnClient(client -> {
                TechniqueVisuals.clear();
                if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
            });
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

    private static void verifyEffect(ClientGameTestContext context, Effect effect, boolean verifyExpiry) {
        var technique = effect.technique();
        int stage = effect.stage(), age = effect.age();
        float pitch = effect.pitch();
        boolean reverse = effect.reverse();
        context.getInput().lookAt(0f, pitch);
        context.waitTick();
        context.runOnClient(client -> TechniqueVisuals.clear());
        var label = stage == TechniqueEvent.BLACK_FLASH
                ? "black-flash"
                : technique.name().toLowerCase(Locale.ROOT);
        var name = label + "-" + (int) pitch + "-" + (reverse ? "back" : "front") + "-" + stage + "-" + age;
        var baseline = capture(context, name + "-before");
        context.runOnClient(client -> {
            var player = requireNonNull(client.player);
            var level = requireNonNull(client.level);
            var direction = player.getLookAngle();
            var destination = player.getEyePosition().add(direction.scale(effect.distance()));
            // A successful contact may start inside the target; zero separation is not a miss.
            var origin = reverse
                    ? destination.add(direction)
                    : technique == Technique.CLEAVE && stage != TechniqueEvent.BLACK_FLASH && pitch == 0
                            ? destination
                            : player.getEyePosition();
            TechniqueVisuals.accept(new TechniqueEvent(
                    level.dimension().identifier(),
                    UUID.randomUUID(),
                    stage == TechniqueEvent.PREPARE ? -1 : player.getId(),
                    technique.wireId(),
                    stage,
                    level.getGameTime() - age,
                    origin,
                    destination));
        });
        checkEffect(context, baseline, name, true);
        if (verifyExpiry) {
            int lifetime = stage == TechniqueEvent.PREPARE ? technique.preparation() : EFFECT_EXPIRY;
            context.waitTicks(Math.max(lifetime - age + 2, 1));
            checkEffect(context, baseline, name + "-expired", false);
        }
    }
}
