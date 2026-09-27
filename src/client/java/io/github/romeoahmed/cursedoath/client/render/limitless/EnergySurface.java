package io.github.romeoahmed.cursedoath.client.render.limitless;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Opaque emissive spheres with a violet projection of the shared fusion volume.
final class EnergySurface {
    private static final double DETAIL_DISTANCE_SQUARED = 48 * 48;
    private static final int[] BLUE = palette(0xFF126AE8, 0xFF22CCFF, 0xFFF2FFFF),
            RED = palette(0xFFDC153C, 0xFFFF5768, 0xFFFFF5EA),
            PURPLE = palette(0xFF7333D8, 0xFFBD70FF, 0xFFFFF2FF);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Cell[] FINE = cells(24), COARSE = cells(12);
    private final PoseStack.Pose pose;
    private final VertexConsumer vertices;

    EnergySurface(PoseStack.Pose pose, VertexConsumer vertices) {
        this.pose = pose;
        this.vertices = vertices;
    }

    void draw(List<LimitlessEffects.Form> forms) {
        for (int i = 0; i < forms.size(); i++) {
            var form = forms.get(i);
            var other = forms.size() == 2 ? forms.get(1 - i) : null;
            if (other != null && form.center().distanceToSqr(other.center()) >= square(form.radius() + other.radius()))
                other = null;
            draw(form, other);
        }
    }

    private void draw(LimitlessEffects.Form form, LimitlessEffects.@Nullable Form other) {
        var eye = viewFrom(pose, Vec3.ZERO);
        var view = eye.subtract(form.center()).normalize();
        var cells = eye.distanceToSqr(form.center()) > DETAIL_DISTANCE_SQUARED ? COARSE : FINE;
        var axes = EffectMesh.basis(form.direction());
        var forward = form.direction().equals(Vec3.ZERO) ? FORWARD : form.direction();
        var side = axes.side();
        var up = axes.up();
        var colors = palette(form.technique());
        double vx = side.dot(view), vy = up.dot(view), vz = forward.dot(view);
        double phase = (Math.sin(form.age() * 0.08) + 1) * 0.5;
        for (var cell : cells) {
            double variation = (cell.first() + (cell.second() - cell.first()) * phase) * 0.075;
            var center = cell.center();
            // Fine, low-contrast color cells preserve a luminous center without a glossy highlight.
            double emission = Math.max(0, center.x * vx + center.y * vy + center.z * vz);
            double tone = Math.clamp(emission * emission + variation * (1 - emission), 0, 1);
            int index = (int) (tone * (colors.length - 1));
            int source = colors[index];
            for (var corner : cell.corners()) {
                double nx = side.x * corner.x + up.x * corner.y + forward.x * corner.z,
                        ny = side.y * corner.x + up.y * corner.y + forward.y * corner.z,
                        nz = side.z * corner.x + up.z * corner.y + forward.z * corner.z;
                double x = form.center().x + nx * form.radius(),
                        y = form.center().y + ny * form.radius(),
                        z = form.center().z + nz * form.radius();
                int color = source;
                if (other != null) {
                    double overlap = overlap(x, y, z, eye, form, other);
                    color = ARGB.srgbLerp((float) overlap, color, PURPLE[index]);
                }
                vertices.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
            }
        }
    }

    // Intersect ray intervals so an oblique view colors the shared volume, not merely an occluded silhouette.
    private static double overlap(
            double x, double y, double z, Vec3 eye, LimitlessEffects.Form source, LimitlessEffects.Form other) {
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) return 0;
        dx /= length;
        dy /= length;
        dz /= length;
        double ax = source.center().x - eye.x, ay = source.center().y - eye.y, az = source.center().z - eye.z;
        double bx = other.center().x - eye.x, by = other.center().y - eye.y, bz = other.center().z - eye.z;
        double alongA = ax * dx + ay * dy + az * dz, alongB = bx * dx + by * dy + bz * dz;
        double depthB = square(other.radius()) - (bx * bx + by * by + bz * bz - alongB * alongB);
        if (depthB <= 0) return 0;
        double halfA =
                Math.sqrt(Math.max(0, square(source.radius()) - (ax * ax + ay * ay + az * az - alongA * alongA)));
        double halfB = Math.sqrt(depthB);
        double shared =
                Math.min(alongA + halfA, alongB + halfB) - Math.max(0, Math.max(alongA - halfA, alongB - halfB));
        return Mth.smoothstep((float) Math.clamp(shared / (source.radius() * 0.2), 0, 1));
    }

    private static int[] palette(Technique technique) {
        return switch (technique) {
            case BLUE -> BLUE;
            case RED -> RED;
            default -> PURPLE;
        };
    }

    private static int[] palette(int rim, int body, int core) {
        var colors = new int[256];
        for (int i = 0; i < colors.length; i++) {
            float tone = i / 255f;
            colors[i] = tone < 0.55f
                    ? ARGB.srgbLerp(tone / 0.55f, rim, body)
                    : ARGB.srgbLerp(Mth.smoothstep((tone - 0.55f) / 0.45f), body, core);
        }
        return colors;
    }

    /// Returns the camera offset from `center` for a camera-relative, translation-only pose.
    /// Rotation and scale must be applied to geometry, not to the supplied pose.
    static Vec3 viewFrom(PoseStack.Pose pose, Vec3 center) {
        var matrix = pose.pose();
        return new Vec3(-matrix.m30(), -matrix.m31(), -matrix.m32()).subtract(center);
    }

    private record Cell(List<Vec3> corners, Vec3 center, double first, double second) {}

    private static Cell[] cells(int resolution) {
        var cells = new ArrayList<Cell>(6 * resolution * resolution);
        for (var normal : List.of(
                new Vec3(1, 0, 0),
                new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0),
                new Vec3(0, -1, 0),
                FORWARD,
                FORWARD.reverse())) {
            var axes = EffectMesh.basis(normal);
            for (int y = 0; y < resolution; y++)
                for (int x = 0; x < resolution; x++) {
                    double left = 2.0 * x / resolution - 1, bottom = 2.0 * y / resolution - 1, step = 2.0 / resolution;
                    var center = normal.add(axes.side().scale(left + step * 0.5))
                            .add(axes.up().scale(bottom + step * 0.5))
                            .normalize();
                    cells.add(new Cell(
                            List.of(
                                    point(normal, axes, left, bottom), point(normal, axes, left + step, bottom),
                                    point(normal, axes, left + step, bottom + step),
                                            point(normal, axes, left, bottom + step)),
                            center,
                            field(center, 0),
                            field(center, 1.7)));
                }
        }
        return cells.toArray(Cell[]::new);
    }

    private static Vec3 point(Vec3 normal, EffectMesh.Basis axes, double x, double y) {
        return normal.add(axes.side().scale(x)).add(axes.up().scale(y)).normalize();
    }

    private static double field(Vec3 point, double phase) {
        return Math.sin(point.y * 9 + Math.sin(point.x * 7 + phase) * 2 + point.z * 4 + phase);
    }

    private static double square(double value) {
        return value * value;
    }
}
