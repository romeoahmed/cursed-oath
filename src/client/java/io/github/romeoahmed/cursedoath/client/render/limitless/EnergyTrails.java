package io.github.romeoahmed.cursedoath.client.render.limitless;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/// Surface flows, soft glow, and directional trails around the opaque body.
public final class EnergyTrails {
    private static final double GLOW_PLANE = 1.01;
    private static final double GLOW_RADIUS = 1.5;
    private static final int GLOW_ALPHA = 100;
    private static final int GLOW_BANDS = 6;
    private static final int GLOW_SEGMENTS = 32;
    private static final int BLUE_RIBBONS = 5;
    private static final int RIBBON_STEPS = 10;
    private static final double BLUE_SPEED = 0.09;
    private static final double BLUE_SWEEP = 2.2;
    private static final double BLUE_REACH = 0.3;
    private static final double BLUE_TWIST = 0.18;
    private static final double SURFACE_OFFSET = 1.015;
    private static final double DARK_WIDTH = 0.18;
    private static final double LIGHT_WIDTH = 0.035;
    private static final int BLUE_INK = 0x062C85;
    private static final int BLUE_LIGHT = 0x24CCFF;
    private static final int RED_RAYS = 12;
    private static final double RED_SPEED = 0.11;
    private static final double RAY_START = 1.1;
    private static final double RAY_TRAVEL = 2.0;
    private static final double RAY_LENGTH = 1.8;
    private static final double RAY_WIDTH = 0.075;
    private static final int RED_LIGHT = 0xFF2948;
    private static final int PURPLE_FLOWS = 7;
    private static final double PURPLE_SPEED = 0.07;
    private static final double PURPLE_SWEEP = 2.0;
    private static final double FLOW_RIPPLE = 0.035;
    private static final int RIPPLE_FREQUENCY = 5;
    private static final double PURPLE_WIDTH = 0.04;
    private static final int PURPLE_LIGHT = 0xDC79FF;
    private static final float FLOW_ALPHA = 0.55f;
    private static final int WAKE_STREAMS = 12;
    private static final double WAKE_SPEED = 0.08;
    private static final double WAKE_RADIUS = 0.7;
    private static final double WAKE_START = 0.45;
    private static final double WAKE_LENGTH = 0.45;
    private static final double WAKE_SPREAD = 0.12;
    private static final double WAKE_WIDTH = 0.035;
    private static final double IMPACT_WIDTH = 0.3;
    private static final double IMPACT_INNER = 0.6;
    private static final double GOLDEN_ANGLE = 2.399963;
    private static final int NORMAL_COUNT = 24;
    private static final Vec3[] NORMALS = new Vec3[NORMAL_COUNT], CIRCLE = new Vec3[GLOW_SEGMENTS + 1];
    private static final EffectMesh.Basis[] AXES = new EffectMesh.Basis[NORMAL_COUNT];

    static {
        for (int i = 0; i < NORMAL_COUNT; i++) {
            double y = 1 - 2 * (i + 0.5) / NORMAL_COUNT, radius = Math.sqrt(1 - y * y);
            NORMALS[i] = new Vec3(Math.cos(i * GOLDEN_ANGLE) * radius, y, Math.sin(i * GOLDEN_ANGLE) * radius);
            AXES[i] = EffectMesh.basis(NORMALS[i]);
        }
        for (int i = 0; i <= GLOW_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / GLOW_SEGMENTS;
            CIRCLE[i] = new Vec3(Math.cos(angle), Math.sin(angle), 0);
        }
    }

    private final PoseStack.Pose pose;
    private final VertexConsumer vertices;
    private final EffectMesh mesh;

    public EnergyTrails(PoseStack.Pose pose, VertexConsumer vertices) {
        this.pose = pose;
        this.vertices = vertices;
        mesh = new EffectMesh(pose, vertices, 1, 1, 255);
    }

    public void draw(LimitlessEffects.Form form) {
        glow(form);
        switch (form.technique()) {
            case BLUE -> blueRibbons(form, false);
            case RED -> redRays(form);
            case PURPLE -> {
                purpleFlow(form);
                if (form.flying()) wake(form);
            }
            default -> {}
        }
    }

    private void glow(LimitlessEffects.Form form) {
        var view = EnergySurface.viewFrom(pose, form.center()).normalize();
        var axes = EffectMesh.basis(view);
        // Keep the billboard in front of the body; intersection cuts a dark disc into the glow.
        var center = form.center().add(view.scale(form.radius() * GLOW_PLANE));
        int color = ARGB.srgbLerp(form.fusion(), form.technique().color(), Technique.PURPLE.color());
        float strength = 1 - form.fusion() / 2;
        for (int band = 0; band < GLOW_BANDS; band++) {
            double inner = (double) band / GLOW_BANDS,
                    outer = (band + 1.0) / GLOW_BANDS,
                    innerRadius = form.radius() * GLOW_RADIUS * inner,
                    outerRadius = form.radius() * GLOW_RADIUS * outer;
            int innerColor = ARGB.color((int) ((1 - inner) * (1 - inner) * GLOW_ALPHA * strength), color),
                    outerColor = ARGB.color((int) ((1 - outer) * (1 - outer) * GLOW_ALPHA * strength), color);
            for (int i = 0; i < GLOW_SEGMENTS; i++) {
                glowVertex(center, axes, CIRCLE[i], innerRadius, innerColor);
                glowVertex(center, axes, CIRCLE[i + 1], innerRadius, innerColor);
                glowVertex(center, axes, CIRCLE[i + 1], outerRadius, outerColor);
                glowVertex(center, axes, CIRCLE[i], outerRadius, outerColor);
            }
        }
    }

    private void glowVertex(Vec3 center, EffectMesh.Basis axes, Vec3 point, double radius, int color) {
        double x = point.x * radius, y = point.y * radius;
        var right = axes.side();
        var up = axes.up();
        vertices.addVertex(
                        pose,
                        (float) (center.x + right.x * x + up.x * y),
                        (float) (center.y + right.y * x + up.y * y),
                        (float) (center.z + right.z * x + up.z * y))
                .setColor(color);
    }

    public void blueRibbons(LimitlessEffects.Form form, boolean dark) {
        for (int i = 0; i < BLUE_RIBBONS; i++) {
            int index = i * NORMAL_COUNT / BLUE_RIBBONS;
            var axis = NORMALS[index];
            var axes = AXES[index];
            double phase = i * GOLDEN_ANGLE - form.age() * BLUE_SPEED;
            for (int step = 0; step < RIBBON_STEPS; step++) {
                double t = (double) step / RIBBON_STEPS;
                var width = axis.scale(form.radius()
                        * (dark ? DARK_WIDTH : LIGHT_WIDTH)
                        * Math.sin(t * Math.PI)
                        * (1 - form.fusion()));
                var a = bluePoint(form, axis, axes, phase, t);
                var b = bluePoint(form, axis, axes, phase, (step + 1.0) / RIBBON_STEPS);
                mesh.ribbon(a, b, width, dark ? BLUE_INK : BLUE_LIGHT);
            }
        }
    }

    private static Vec3 bluePoint(
            LimitlessEffects.Form form, Vec3 axis, EffectMesh.Basis axes, double phase, double t) {
        double angle = phase + t * BLUE_SWEEP,
                radius = form.radius() * (SURFACE_OFFSET + BLUE_REACH * (1 - t) * (1 - t));
        return form.center()
                .add(axes.side().scale(Math.cos(angle) * radius))
                .add(axes.up().scale(Math.sin(angle) * radius))
                .add(axis.scale(form.radius() * BLUE_TWIST * Math.sin(t * Math.PI)));
    }

    private void redRays(LimitlessEffects.Form form) {
        for (int i = 0; i < RED_RAYS; i++) {
            int index = i * NORMAL_COUNT / RED_RAYS;
            var direction = NORMALS[index];
            double phase = (form.age() * RED_SPEED + (double) i / RED_RAYS) % 1,
                    reach = form.radius() * (RAY_START + phase * RAY_TRAVEL);
            var width = AXES[index].side().scale(form.radius() * RAY_WIDTH * (1 - phase) * (1 - form.fusion()));
            var a = form.center().add(direction.scale(reach));
            var b = form.center().add(direction.scale(reach + form.radius() * RAY_LENGTH));
            mesh.slash(a, b, width, RED_LIGHT, (float) (1 - phase));
        }
    }

    private void purpleFlow(LimitlessEffects.Form form) {
        for (int i = 0; i < PURPLE_FLOWS; i++) {
            int index = i * NORMAL_COUNT / PURPLE_FLOWS;
            var axis = NORMALS[index];
            var right = AXES[index].side();
            var up = AXES[index].up();
            double phase = i * GOLDEN_ANGLE + form.age() * PURPLE_SPEED;
            for (int step = 0; step < RIBBON_STEPS; step++) {
                double t = (double) step / RIBBON_STEPS,
                        angle = phase + t * PURPLE_SWEEP,
                        radius =
                                form.radius()
                                        * (SURFACE_OFFSET + FLOW_RIPPLE * (1 + Math.sin(angle * RIPPLE_FREQUENCY)));
                var a = form.center()
                        .add(right.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                double next = angle + PURPLE_SWEEP / RIBBON_STEPS;
                var b = form.center().add(right.scale(Math.cos(next) * radius)).add(up.scale(Math.sin(next) * radius));
                var width = axis.scale(form.radius() * PURPLE_WIDTH * Math.sin(t * Math.PI));
                mesh.ribbon(
                        a,
                        b,
                        width,
                        i % 2 == 0 ? PURPLE_LIGHT : form.technique().color(),
                        FLOW_ALPHA);
            }
        }
    }

    private void wake(LimitlessEffects.Form form) {
        var axes = EffectMesh.basis(form.direction());
        var right = axes.side();
        var up = axes.up();
        for (int i = 0; i < WAKE_STREAMS; i++) {
            double phase = (form.age() * WAKE_SPEED + (double) i / WAKE_STREAMS) % 1, angle = i * GOLDEN_ANGLE;
            var radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            var start = form.center()
                    .add(radial.scale(form.radius() * WAKE_RADIUS))
                    .subtract(form.direction().scale(form.radius() * (WAKE_START + phase)));
            var end = start.subtract(form.direction().scale(form.radius() * WAKE_LENGTH))
                    .add(radial.scale(form.radius() * WAKE_SPREAD));
            mesh.slash(start, end, radial.scale(form.radius() * WAKE_WIDTH), PURPLE_LIGHT, (float)
                    ((1 - phase) * FLOW_ALPHA));
        }
    }

    public void impact(float progress) {
        double radius = Math.sqrt(progress) * TechniqueTuning.RED_RADIUS;
        float alpha = (1 - progress) * (1 - progress);
        for (int index = 0; index < NORMAL_COUNT; index++) {
            var normal = NORMALS[index];
            var width = AXES[index].side().scale(IMPACT_WIDTH * alpha);
            mesh.slash(normal.scale(radius * IMPACT_INNER), normal.scale(radius), width, RED_LIGHT, alpha);
        }
    }
}
