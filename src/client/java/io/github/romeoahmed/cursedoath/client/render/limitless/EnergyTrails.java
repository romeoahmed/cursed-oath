package io.github.romeoahmed.cursedoath.client.render.limitless;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/// Soft glow and directional trails around the opaque body.
public final class EnergyTrails {
    private static final double GLOW_PLANE = 1.01;
    private static final double GLOW_RADIUS = 1.25;
    private static final int GLOW_ALPHA = 24;
    private static final int GLOW_BANDS = 5;
    private static final int GLOW_SEGMENTS = 32;
    private static final int RED_RAYS = 6, WAKE_STREAMS = 8;
    private static final int RED_LIGHT = 0xFF2948, PURPLE_LIGHT = 0xD890FF;
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
        if (form.accentStrength() <= 0) return;
        glow(form);
        switch (form.technique()) {
            case RED -> redRays(form);
            case PURPLE -> wake(form);
            default -> {}
        }
    }

    private void glow(LimitlessEffects.Form form) {
        var view = EnergySurface.viewFrom(pose, form.center()).normalize();
        var axes = EffectMesh.basis(view);
        // Keep the billboard in front of the body; intersection cuts a dark disc into the glow.
        var center = form.center().add(view.scale(form.radius() * GLOW_PLANE));
        int color = form.technique().color();
        for (int band = 0; band < GLOW_BANDS; band++) {
            double inner = (double) band / GLOW_BANDS,
                    outer = (band + 1.0) / GLOW_BANDS,
                    innerRadius = form.radius() * GLOW_RADIUS * inner,
                    outerRadius = form.radius() * GLOW_RADIUS * outer;
            int innerColor = ARGB.color((int) ((1 - inner) * (1 - inner) * GLOW_ALPHA * form.accentStrength()), color),
                    outerColor =
                            ARGB.color((int) ((1 - outer) * (1 - outer) * GLOW_ALPHA * form.accentStrength()), color);
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

    private void redRays(LimitlessEffects.Form form) {
        for (int i = 0; i < RED_RAYS; i++) {
            int index = i * NORMAL_COUNT / RED_RAYS;
            var direction = NORMALS[index];
            double phase = (form.age() * 0.09 + i * 0.37) % 1;
            double reach = form.radius() * (1 + phase * 1.8);
            var a = form.center().add(direction.scale(reach));
            var b = form.center().add(direction.scale(reach + form.radius() * (0.7 + phase * 0.7)));
            var view = EnergySurface.viewFrom(pose, a).normalize();
            var width = direction.cross(view).normalize().scale(form.radius() * 0.18 * (1 - phase));
            float alpha = (float) (Math.sin(Math.PI * phase) * form.accentStrength());
            mesh.ribbon(a, b, width, Vec3.ZERO, RED_LIGHT, alpha);
        }
    }

    private void wake(LimitlessEffects.Form form) {
        if (form.trailStrength() <= 0) return;
        var axes = EffectMesh.basis(form.direction());
        for (int i = 0; i < WAKE_STREAMS; i++) {
            double phase = (form.age() * 0.06 + i * 0.31) % 1, angle = i * GOLDEN_ANGLE;
            var radial = axes.side().scale(Math.cos(angle)).add(axes.up().scale(Math.sin(angle)));
            var tangent = axes.side().scale(-Math.sin(angle)).add(axes.up().scale(Math.cos(angle)));
            double length = form.trailStrength() * (0.5 + phase * 0.65);
            var start = form.center().add(radial.scale(form.radius() * (0.92 + phase * 0.12)));
            var middle = start.subtract(form.direction().scale(form.radius() * length * 0.45))
                    .add(tangent.scale(form.radius() * 0.09));
            var end = start.subtract(form.direction().scale(form.radius() * length))
                    .add(radial.scale(form.radius() * 0.08));
            var width = tangent.scale(form.radius() * 0.09 * Math.sin(phase * Math.PI));
            float alpha = (float) (Math.sin(phase * Math.PI) * 0.65 * form.trailStrength());
            mesh.ribbon(start, middle, width, width.scale(0.7), PURPLE_LIGHT, alpha);
            mesh.ribbon(middle, end, width.scale(0.7), Vec3.ZERO, PURPLE_LIGHT, alpha);
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
