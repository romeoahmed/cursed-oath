package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation;
import io.github.romeoahmed.cursedoath.client.render.limitless.BlueDebris;
import io.github.romeoahmed.cursedoath.client.render.limitless.EnergyTrails;
import io.github.romeoahmed.cursedoath.client.render.limitless.LimitlessEffects;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class TechniqueVisuals {
    private static final int MAX_DEBRIS_FIELDS = 8, MAX_EFFECTS = 96, MAX_SEEN = 512, IMPACT_DURATION = 16;
    private static final double MAX_DISTANCE_SQUARED = 128.0 * 128.0, EFFECT_SIZE = 32;

    private record Effect(TechniqueEvent event, Technique technique, int duration) {}

    private record Shape(Vec3 position, Vec3 direction, Technique technique, float progress, int stage) {}

    private record EventKey(UUID id, int stage) {}

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final Set<TechniqueOrb> ORBS = new LinkedHashSet<>();
    private static final LinkedHashSet<EventKey> SEEN = new LinkedHashSet<>();
    private static final RenderStateDataKey<List<Shape>> KEY = RenderStateDataKey.create(() -> "cursed-oath:effects");

    private TechniqueVisuals() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TechniqueVisuals::tickDebris);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TechniqueOrb orb) ORBS.add(orb);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TechniqueOrb orb) ORBS.remove(orb);
        });
        var renderType = EffectRenderTypes.ADDITIVE;
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            double time = (double) context.level().getGameTime() + partial;
            EFFECTS.removeIf(effect -> !effect.event()
                            .dimension()
                            .equals(context.level().dimension().identifier())
                    || time - effect.event().tick() > effect.duration());
            context.levelState()
                    .setData(
                            KEY,
                            EFFECTS.stream()
                                    .map(effect -> shape(effect, context.level(), time, partial))
                                    .toList());
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            var shapes = context.levelState().getData(KEY);
            if (shapes == null) return;
            var cameraState = context.levelState().cameraRenderState;
            var camera = cameraState.pos;
            for (var shape : shapes) {
                if (shape.position().distanceToSqr(camera) > MAX_DISTANCE_SQUARED
                        || !cameraState.cullFrustum.isVisible(
                                AABB.ofSize(shape.position(), EFFECT_SIZE, EFFECT_SIZE, EFFECT_SIZE))) continue;
                var pose = context.poseStack();
                pose.pushPose();
                pose.translate(
                        shape.position().x - camera.x, shape.position().y - camera.y, shape.position().z - camera.z);
                var collector = context.submitNodeCollector();
                if (shape.stage() == TechniqueEvent.PREPARE)
                    LimitlessEffects.submit(
                            pose,
                            collector,
                            LimitlessEffects.charge(shape.technique(), shape.progress(), shape.direction()));
                else {
                    collector.submitCustomGeometry(
                            pose,
                            shape.technique() == Technique.CLEAVE && shape.stage() != TechniqueEvent.BLACK_FLASH
                                    ? EffectRenderTypes.CORE
                                    : renderType,
                            (matrix, vertices) -> {
                                switch (shape.stage()) {
                                    case TechniqueEvent.IMPACT ->
                                        new EnergyTrails(matrix, vertices).impact(shape.progress());
                                    case TechniqueEvent.BLACK_FLASH ->
                                        new TechniqueGeometry(new EffectMesh(matrix, vertices))
                                                .blackFlash(shape.direction(), shape.progress());
                                    default ->
                                        new TechniqueGeometry(new EffectMesh(matrix, vertices))
                                                .draw(shape.technique(), shape.progress(), shape.direction());
                                }
                            });
                    drawCore(pose, collector, shape);
                }
                pose.popPose();
            }
        });
    }

    private static void drawCore(PoseStack pose, SubmitNodeCollector collector, Shape shape) {
        if (shape.stage() != TechniqueEvent.BLACK_FLASH) return;
        collector.submitCustomGeometry(
                pose,
                EffectRenderTypes.CORE,
                (matrix, vertices) -> new TechniqueGeometry(new EffectMesh(matrix, vertices, 1, 1, 255))
                        .blackFlash(shape.direction(), shape.progress(), true));
    }

    private static void tickDebris(Minecraft client) {
        if (client.isPaused()) return;
        int remaining = MAX_DEBRIS_FIELDS;
        for (var orb : ORBS)
            if (orb.technique() == Technique.BLUE && BlueDebris.emit(client, orb.position()) && --remaining == 0) break;
    }

    private static Shape shape(Effect effect, ClientLevel level, double time, float partial) {
        float age = (float) Math.max(0, time - effect.event().tick());
        var actor = effect.event().stage() == TechniqueEvent.PREPARE
                ? level.getEntity(effect.event().actor())
                : null;
        var direction = actor == null
                ? effect.event().destination().subtract(effect.event().origin()).normalize()
                : actor.getViewVector(partial);
        double distance = switch (effect.technique()) {
            case BLUE, RED, PURPLE -> TechniqueTuning.launchDistance(effect.technique());
            default -> 1.2;
        };
        var position = actor == null
                ? effect.event().destination()
                : actor.getEyePosition(partial).add(direction.scale(distance));
        return new Shape(
                position,
                direction,
                effect.technique(),
                Math.clamp(age / effect.duration(), 0f, 1f),
                effect.event().stage());
    }

    public static void accept(TechniqueEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !event.dimension().equals(level.dimension().identifier())) return;
        var technique = Technique.fromWire(event.technique());
        if (technique == null || !SEEN.add(new EventKey(event.id(), event.stage()))) return;
        while (SEEN.size() > MAX_SEEN) SEEN.removeFirst();
        long age = Math.max(0, level.getGameTime() - event.tick());
        var entity = level.getEntity(event.actor());
        Avatar actor = entity instanceof Avatar avatar ? avatar : null;
        switch (event.stage()) {
            case TechniqueEvent.PREPARE -> prepare(actor, event, technique, age);
            case TechniqueEvent.CANCEL, TechniqueEvent.RELEASE -> {
                if (actor != null) animateRelease(actor, event, technique, age);
                finishEvent(event, technique, age);
            }
            case TechniqueEvent.IMPACT -> {
                if (technique == Technique.RED) addEffect(event, technique, age, IMPACT_DURATION);
            }
            case TechniqueEvent.BLACK_FLASH -> addEffect(event, technique, age, IMPACT_DURATION);
            default -> {}
        }
    }

    private static void prepare(@Nullable Avatar actor, TechniqueEvent event, Technique technique, long age) {
        if (actor != null && age < technique.preparation())
            CastingAnimation.start(actor, technique, false, (float) age);
        if (technique == Technique.PURPLE || technique == Technique.BLUE || technique == Technique.RED)
            addEffect(event, technique, age, technique.preparation());
    }

    private static void animateRelease(Avatar actor, TechniqueEvent event, Technique technique, long age) {
        CastingAnimation.stop(actor);
        if (event.stage() == TechniqueEvent.RELEASE && age < IMPACT_DURATION)
            CastingAnimation.start(actor, technique, true, (float) age);
    }

    private static void finishEvent(TechniqueEvent event, Technique technique, long age) {
        EFFECTS.removeIf(effect ->
                effect.event().id().equals(event.id()) && effect.event().stage() == TechniqueEvent.PREPARE);
        if (event.stage() == TechniqueEvent.RELEASE && (technique == Technique.CLEAVE || technique == Technique.HEAL))
            addEffect(event, technique, age, IMPACT_DURATION);
    }

    private static void addEffect(TechniqueEvent event, Technique technique, long age, int duration) {
        if (age >= duration) return;
        if (EFFECTS.size() == MAX_EFFECTS) EFFECTS.removeFirst();
        EFFECTS.add(new Effect(event, technique, duration));
    }

    public static void clear() {
        ORBS.clear();
        EFFECTS.clear();
        SEEN.clear();
    }
}
