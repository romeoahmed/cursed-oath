package io.github.romeoahmed.cursedoath.client.render.limitless;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/// Opaque energy volume with flat color cells and view-facing brightness.
final class EnergySurface {
    private static final double FLOW_SPEED = 0.28;
    private static final double FLOW_SCALE = 13.0;
    private static final double FLOW_TILT = 9.0;
    private static final double FLOW_DEPTH = 17.0;
    private static final double FLOW_CONTRAST = 0.12;
    private static final double GRAIN_STRENGTH = 0.02;
    private static final double GRAIN_X = 12.9898;
    private static final double GRAIN_Y = 78.233;
    private static final int COLOR_STEPS = 8;
    private static final double HOT_THRESHOLD = 0.6;
    private static final double DETAIL_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final int[] BLUE = palette(0xFF064BCD, 0xFF26DFFF, 0xFFF1FFFF),
            RED = palette(0xFFCA0925, 0xFFFF485C, 0xFFFFF9F1),
            PURPLE = palette(0xFF6D14BA, 0xFFCD5CFF, 0xFFFFEEFF);
    private static final Vec3[] AXES = {
        new Vec3(1, 0, 0),
        new Vec3(-1, 0, 0),
        new Vec3(0, 1, 0),
        new Vec3(0, -1, 0),
        new Vec3(0, 0, 1),
        new Vec3(0, 0, -1)
    };
    private static final Cell[] FINE = cells(32), COARSE = cells(16);
    private final PoseStack.Pose pose;
    private final VertexConsumer vertices;

    EnergySurface(PoseStack.Pose pose, VertexConsumer vertices) {
        this.pose = pose;
        this.vertices = vertices;
    }

    void draw(LimitlessEffects.Form form) {
        var view = viewFrom(pose, form.center());
        var cells = view.lengthSqr() > DETAIL_DISTANCE_SQUARED ? COARSE : FINE;
        var eye = view.normalize();
        var palette = switch (form.technique()) {
            case BLUE -> BLUE;
            case RED -> RED;
            default -> PURPLE;
        };
        var colors = palette;
        if (form.fusion() > 0) {
            colors = new int[palette.length];
            for (int i = 0; i < palette.length; i++) colors[i] = ARGB.srgbLerp(form.fusion(), palette[i], PURPLE[i]);
        }
        // sin(time + space) reuses cached spatial phases across cells.
        double phase = form.age() * FLOW_SPEED, sine = Math.sin(phase), cosine = Math.cos(phase);
        for (var cell : cells) {
            double facing = Math.max(0, cell.normal().dot(eye)), motion = sine * cell.cosine() + cosine * cell.sine();
            double tone = Math.clamp(facing * facing + motion * FLOW_CONTRAST + cell.grain(), 0, 1);
            int color = colors[(int) (tone * COLOR_STEPS)];
            for (var point : cell.corners())
                vertices.addVertex(
                                pose,
                                (float) (form.center().x + point.x * form.radius()),
                                (float) (form.center().y + point.y * form.radius()),
                                (float) (form.center().z + point.z * form.radius()))
                        .setColor(color);
        }
        if (form.technique() == Technique.BLUE) new EnergyTrails(pose, vertices).blueRibbons(form, true);
    }

    private record Cell(List<Vec3> corners, Vec3 normal, double sine, double cosine, double grain) {}

    private static int[] palette(int low, int middle, int high) {
        var colors = new int[COLOR_STEPS + 1];
        for (int index = 0; index <= COLOR_STEPS; index++) {
            double band = (double) index / COLOR_STEPS;
            colors[index] = band < HOT_THRESHOLD
                    ? ARGB.srgbLerp((float) (band / HOT_THRESHOLD), low, middle)
                    : ARGB.srgbLerp((float) ((band - HOT_THRESHOLD) / (1 - HOT_THRESHOLD)), middle, high);
        }
        return colors;
    }

    static Vec3 viewFrom(PoseStack.Pose pose, Vec3 center) {
        var matrix = pose.pose();
        return new Vec3(-matrix.m30(), -matrix.m31(), -matrix.m32()).subtract(center);
    }

    private static Vec3 point(Vec3 normal, EffectMesh.Basis axes, int x, int y, int resolution) {
        return normal.add(axes.side().scale(x * 2.0 / resolution - 1))
                .add(axes.up().scale(y * 2.0 / resolution - 1))
                .normalize();
    }

    private static Cell[] cells(int resolution) {
        var cells = new Cell[AXES.length * resolution * resolution];
        int index = 0;
        for (var normal : AXES) {
            var axes = EffectMesh.basis(normal);
            for (int y = 0; y < resolution; y++)
                for (int x = 0; x < resolution; x++) {
                    var corners = new Vec3[] {
                        point(normal, axes, x, y, resolution),
                        point(normal, axes, x + 1, y, resolution),
                        point(normal, axes, x + 1, y + 1, resolution),
                        point(normal, axes, x, y + 1, resolution)
                    };
                    var center = corners[0].add(corners[2]).normalize();
                    double phase = center.x * FLOW_SCALE + center.y * FLOW_TILT + center.z * FLOW_DEPTH;
                    double grain = Math.sin(x * GRAIN_X + y * GRAIN_Y + normal.x) * GRAIN_STRENGTH;
                    cells[index++] = new Cell(List.of(corners), center, Math.sin(phase), Math.cos(phase), grain);
                }
        }
        return cells;
    }
}
