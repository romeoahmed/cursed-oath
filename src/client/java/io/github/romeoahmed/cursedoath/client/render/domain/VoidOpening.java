package io.github.romeoahmed.cursedoath.client.render.domain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

/// Renders the transition into the panorama from synchronized domain age.
final class VoidOpening {
    static final int DURATION = 24;
    private static final Strand[] STRANDS = new Strand[144];

    static {
        for (int i = 0; i < STRANDS.length; i++) {
            int group = i / 3, lane = i % 3;
            double angle = group * 2.399963229728653 + lane * 0.012;
            double offset = 0.055 + 0.46 * ((group * 29 % 47) / 47.0) + lane * 0.008;
            STRANDS[i] = new Strand(
                    Math.cos(angle),
                    Math.sin(angle),
                    offset,
                    0.17 + (i * 13 % 19) * 0.017,
                    0.0024 + (i % 4) * 0.0007,
                    (i * 7 % 17) / 17f,
                    lane == 0 ? 0xECE1ED : lane == 1 ? 0xB49AD4 : 0x816394);
        }
    }

    private VoidOpening() {}

    static float reveal(float age) {
        return smooth((age - 10) / (DURATION - 10));
    }

    static int backdrop(float age, boolean subdued) {
        float darkness = smooth((age - 1) / 5);
        int color = ARGB.srgbLerp(darkness, 0xFFE8E1E5, 0xFF100C1D);
        float opacity = 1 - smooth((age - 10) / 8);
        if (subdued) opacity *= 0.12f;
        return ARGB.color(opacity, color);
    }

    static void draw(PoseStack.Pose pose, VertexConsumer vertices, double radius, float age, boolean subdued) {
        if (age < 0 || age >= DURATION) return;
        float envelope = smooth((age - 3) / 4) * (1 - smooth((age - 12) / 9));
        if (envelope <= 0) return;
        float travel = smooth((age - 3) / 17);
        for (int i = 0; i < STRANDS.length; i++) {
            if (subdued && i % 3 != 0) continue;
            var strand = STRANDS[i];
            // Fixed lanes and staggered depths produce parallax without spinning the viewer's sky.
            double depth = 0.82 - travel * 0.46 + strand.phase * 0.12;
            double x = strand.cos * strand.offset, y = strand.sin * strand.offset;
            double near = Math.max(0.08, depth - strand.length);
            float opacity = envelope * (subdued ? 0.2f : 1f) * (0.65f + strand.phase * 0.35f);
            streak(pose, vertices, radius, strand, x, y, depth, near, 4, 0x72538F, opacity * 0.18f);
            streak(pose, vertices, radius, strand, x, y, depth, near, 1, strand.color, opacity);
        }
    }

    private static void streak(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            double radius,
            Strand strand,
            double x,
            double y,
            double far,
            double near,
            double width,
            int rgb,
            float opacity) {
        double dx = -strand.sin * strand.width * width, dy = strand.cos * strand.width * width;
        // Faded ends keep adjacent strands from reading as a wireframe or a solid tunnel wall.
        int clear = ARGB.color(0, rgb), bright = ARGB.color(opacity, rgb);
        double middle = far * 0.4 + near * 0.6;
        vertex(pose, vertices, radius, x, y, far, clear);
        vertex(pose, vertices, radius, x + dx, y + dy, middle, bright);
        vertex(pose, vertices, radius, x, y, near, clear);
        vertex(pose, vertices, radius, x - dx, y - dy, middle, bright);
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer vertices, double radius, double x, double y, double z, int color) {
        vertices.addVertex(pose, (float) (x * radius), (float) (y * radius), (float) (z * radius))
                .setColor(color);
    }

    private static float smooth(float progress) {
        float t = Math.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record Strand(double cos, double sin, double offset, double length, double width, float phase, int color) {}
}
