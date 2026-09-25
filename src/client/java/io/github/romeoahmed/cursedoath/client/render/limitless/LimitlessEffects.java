package io.github.romeoahmed.cursedoath.client.render.limitless;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/// Immutable forms are shared by the depth-writing body and additive flow passes.
public final class LimitlessEffects {
    public static final double CHARGE_DISTANCE = 4.0;
    public static final double VISUAL_RADIUS = 16.0;
    private static final double BLUE_RADIUS = 1.4;
    private static final double BLUE_CHARGE = 0.6;
    private static final double RED_RADIUS = 0.18;
    private static final double FUSION_RADIUS = 0.45;
    private static final double MERGED_RADIUS = 0.65;
    private static final double RELEASE_RADIUS = 1.6;
    private static final float MERGE_DURATION = 0.12f;
    private static final float RELEASE_TICKS = 4f;
    private static final double SEPARATION = 2.2;
    private static final double CHARGE_START = 0.4;
    private static final double CHARGE_GROWTH = 0.6;
    private static final int FADE_SPEED = 6;

    private LimitlessEffects() {}

    public record Form(
            Technique technique, Vec3 center, Vec3 direction, double radius, float age, boolean flying, float fusion) {
        public Form(Technique technique, Vec3 center, Vec3 direction, double radius, float age) {
            this(technique, center, direction, radius, age, false, 0);
        }
    }

    public static List<Form> charge(Technique technique, float progress, Vec3 direction) {
        float age = progress * technique.preparation();
        if (technique == Technique.PURPLE) return fusion(progress, direction, age);
        double radius = technique == Technique.BLUE ? BLUE_CHARGE : RED_RADIUS;
        return List.of(
                new Form(technique, Vec3.ZERO, direction, radius * (CHARGE_START + progress * CHARGE_GROWTH), age));
    }

    private static List<Form> fusion(float progress, Vec3 direction, float age) {
        float contact = TechniqueTuning.FUSION_CONTACT, merging = smooth((progress - contact) / MERGE_DURATION);
        if (progress < contact + MERGE_DURATION) {
            float approach = smooth(progress / contact);
            double separation = (SEPARATION + (FUSION_RADIUS - SEPARATION) * approach) * (1 - merging);
            var offset = EffectMesh.basis(direction).side().scale(separation);
            double radius = FUSION_RADIUS + (MERGED_RADIUS - FUSION_RADIUS) * merging;
            return List.of(
                    new Form(Technique.BLUE, offset, direction, radius, age, false, merging),
                    new Form(Technique.RED, offset.reverse(), direction, radius, age, false, merging));
        }
        float growth = smooth((progress - contact - MERGE_DURATION) / (1 - contact - MERGE_DURATION));
        return List.of(new Form(
                Technique.PURPLE,
                Vec3.ZERO,
                direction,
                MERGED_RADIUS + (RELEASE_RADIUS - MERGED_RADIUS) * growth,
                age));
    }

    public static List<Form> flight(Technique technique, float age, Vec3 direction) {
        double radius;
        switch (technique) {
            case BLUE ->
                radius = BLUE_RADIUS * Math.clamp((1 - age / TechniqueTuning.BLUE_DURATION) * FADE_SPEED, 0f, 1f);
            case RED -> radius = RED_RADIUS;
            case PURPLE ->
                radius =
                        RELEASE_RADIUS + (TechniqueTuning.PURPLE_RADIUS - RELEASE_RADIUS) * smooth(age / RELEASE_TICKS);
            default -> {
                return List.of();
            }
        }
        var center = technique == Technique.PURPLE
                ? direction.scale(CHARGE_DISTANCE * (1 - smooth(age / RELEASE_TICKS)))
                : Vec3.ZERO;
        // Purple retains its surface phase across the preparation/projectile handoff.
        float visualAge = technique == Technique.PURPLE ? age + technique.preparation() : age;
        return List.of(new Form(technique, center, direction, radius, visualAge, true, 0));
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, List<Form> forms) {
        submit(pose, collector, forms, 1);
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, List<Form> forms, float opacity) {
        if (forms.isEmpty()) return;
        collector.submitCustomGeometry(pose, EffectRenderTypes.SOLID, (matrix, vertices) -> {
            var surface = new EnergySurface(matrix, vertices);
            for (var form : forms) surface.draw(form);
        });
        collector.submitCustomGeometry(pose, EffectRenderTypes.ADDITIVE, (matrix, vertices) -> {
            var trails = new EnergyTrails(matrix, vertices, opacity);
            for (var form : forms) trails.draw(form);
        });
    }

    private static float smooth(float value) {
        return Mth.smoothstep(Math.clamp(value, 0f, 1f));
    }
}
