package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/// Cached unit geometry, submitted through Minecraft's render pipeline.
public final class EffectMesh {
    private static final int SEGMENTS = 32;
    private static final int LATITUDES = 8;
    private static final double INNER_RADIUS = 0.94;
    private static final int MAX_ALPHA = 160;
    private static final float CORE_ALPHA = 0.55f;
    private static final int ALPHA_SHIFT = 24;
    private static final double VERTICAL_THRESHOLD = 0.99;
    private static final Vec3[] CIRCLE = new Vec3[SEGMENTS + 1];
    private static final Vec3[][] SPHERE = new Vec3[LATITUDES + 1][SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++)
            CIRCLE[i] = new Vec3(Math.cos(i * 2 * Math.PI / SEGMENTS), Math.sin(i * 2 * Math.PI / SEGMENTS), 0);
        for (int lat = 0; lat <= LATITUDES; lat++) {
            double angle = -Math.PI / 2 + lat * Math.PI / LATITUDES;
            for (int i = 0; i <= SEGMENTS; i++)
                SPHERE[lat][i] =
                        new Vec3(CIRCLE[i].x * Math.cos(angle), Math.sin(angle), CIRCLE[i].y * Math.cos(angle));
        }
    }

    private final PoseStack.Pose pose;
    private final VertexConsumer vertices;
    private final float opacity;
    private final int detail, maxAlpha;

    public EffectMesh(PoseStack.Pose pose, VertexConsumer vertices) {
        this(pose, vertices, 1, 1, MAX_ALPHA);
    }

    public EffectMesh(PoseStack.Pose pose, VertexConsumer vertices, float opacity) {
        this(pose, vertices, opacity, 1, MAX_ALPHA);
    }

    public EffectMesh(PoseStack.Pose pose, VertexConsumer vertices, float opacity, int detail) {
        this(pose, vertices, opacity, detail, MAX_ALPHA);
    }

    public EffectMesh(PoseStack.Pose pose, VertexConsumer vertices, float opacity, int detail, int maxAlpha) {
        this.pose = pose;
        this.vertices = vertices;
        this.opacity = opacity;
        this.detail = detail;
        this.maxAlpha = maxAlpha;
    }

    public void ring(Vec3 center, Basis axes, double radius, int rgb) {
        ring(center, axes, radius, rgb, 1);
    }

    public void ring(Vec3 center, Basis axes, double radius, int rgb, float alpha) {
        int color = color(rgb, alpha);
        for (int i = 0; i < SEGMENTS; i += detail) {
            ringVertex(center, axes, CIRCLE[i], radius * INNER_RADIUS, color);
            ringVertex(center, axes, CIRCLE[i], radius, color);
            ringVertex(center, axes, CIRCLE[i + detail], radius, color);
            ringVertex(center, axes, CIRCLE[i + detail], radius * INNER_RADIUS, color);
        }
    }

    public void sphere(Vec3 center, double radius, int rgb) {
        sphere(center, radius, rgb, 1);
    }

    public void sphere(Vec3 center, double radius, int rgb, float alpha) {
        int color = color(rgb, alpha * CORE_ALPHA);
        for (int lat = 0; lat < LATITUDES; lat += detail)
            for (int i = 0; i < SEGMENTS; i += detail) {
                sphereVertex(center, SPHERE[lat][i], radius, color);
                sphereVertex(center, SPHERE[lat + detail][i], radius, color);
                sphereVertex(center, SPHERE[lat + detail][i + detail], radius, color);
                sphereVertex(center, SPHERE[lat][i + detail], radius, color);
            }
    }

    private void sphereVertex(Vec3 center, Vec3 point, double radius, int color) {
        vertices.addVertex(pose, (float) (center.x + point.x * radius), (float) (center.y + point.y * radius), (float)
                        (center.z + point.z * radius))
                .setColor(color);
    }

    private void ringVertex(Vec3 center, Basis axes, Vec3 point, double radius, int color) {
        var side = axes.side();
        var up = axes.up();
        double x = point.x * radius, y = point.y * radius;
        vertices.addVertex(
                        pose,
                        (float) (center.x + side.x * x + up.x * y),
                        (float) (center.y + side.y * x + up.y * y),
                        (float) (center.z + side.z * x + up.z * y))
                .setColor(color);
    }

    public void ribbon(Vec3[] points, Vec3 width, int rgb, float alpha) {
        int color = color(rgb, alpha);
        for (int i = 0; i < points.length - 1; i++) ribbon(points[i], points[i + 1], width, width, color);
    }

    public void ribbon(Vec3 start, Vec3 end, Vec3 width, int rgb) {
        ribbon(start, end, width, rgb, 1);
    }

    public void ribbon(Vec3 start, Vec3 end, Vec3 width, int rgb, float alpha) {
        ribbon(start, end, width, width, color(rgb, alpha));
    }

    public void ribbon(Vec3 start, Vec3 end, Vec3 startWidth, Vec3 endWidth, int rgb, float alpha) {
        ribbon(start, end, startWidth, endWidth, color(rgb, alpha));
    }

    private void ribbon(Vec3 start, Vec3 end, Vec3 startWidth, Vec3 endWidth, int color) {
        vertex(start, color);
        vertex(start.x + startWidth.x, start.y + startWidth.y, start.z + startWidth.z, color);
        vertex(end.x + endWidth.x, end.y + endWidth.y, end.z + endWidth.z, color);
        vertex(end, color);
    }

    public void slash(Vec3 a, Vec3 b, Vec3 width, int rgb, float alpha) {
        double x = (a.x + b.x) * 0.5, y = (a.y + b.y) * 0.5, z = (a.z + b.z) * 0.5;
        int color = color(rgb, alpha);
        vertex(a, color);
        vertex(x + width.x, y + width.y, z + width.z, color);
        vertex(b, color);
        vertex(x - width.x, y - width.y, z - width.z, color);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(a, color);
        vertex(b, color);
        vertex(c, color);
        vertex(d, color);
    }

    private void vertex(Vec3 point, int color) {
        vertex(point.x, point.y, point.z, color);
    }

    private void vertex(double x, double y, double z, int color) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }

    private int color(int rgb, float alpha) {
        return ((int) (Math.clamp(alpha, 0f, 1f) * opacity * maxAlpha) << ALPHA_SHIFT) | rgb;
    }

    public record Basis(Vec3 side, Vec3 up) {}

    public static Basis basis(Vec3 direction) {
        var forward = direction.equals(Vec3.ZERO) ? new Vec3(0, 0, 1) : direction;
        var reference = Math.abs(forward.y) < VERTICAL_THRESHOLD ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        var side = forward.cross(reference).normalize();
        return new Basis(side, side.cross(forward).normalize());
    }
}
