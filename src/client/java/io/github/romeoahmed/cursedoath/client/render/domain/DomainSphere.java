package io.github.romeoahmed.cursedoath.client.render.domain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

final class DomainSphere {
    private static final int LONGITUDES = 64;
    private static final int LATITUDES = 32;
    private static final int CHANNEL = 255;
    private static final float REVEAL_TICKS = 24f;
    private static final double VIEW_LONGITUDE = 0.555;
    private static final Vec3[][] POINTS = new Vec3[LATITUDES + 1][LONGITUDES + 1];

    static {
        for (int latitude = 0; latitude <= LATITUDES; latitude++) {
            double pitch = -Math.PI / 2 + latitude * Math.PI / LATITUDES;
            for (int longitude = 0; longitude <= LONGITUDES; longitude++) {
                double yaw = ((double) longitude / LONGITUDES - VIEW_LONGITUDE) * 2 * Math.PI;
                POINTS[latitude][longitude] =
                        new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            }
        }
    }

    private DomainSphere() {}

    static void draw(PoseStack.Pose pose, VertexConsumer vertices, double radius, float age, boolean interior) {
        int alpha = (int) (Math.clamp(age / REVEAL_TICKS, 0f, 1f) * CHANNEL);
        for (int latitude = 0; latitude < LATITUDES; latitude++)
            for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                vertex(pose, vertices, radius, interior, alpha, latitude, longitude);
                vertex(pose, vertices, radius, interior, alpha, latitude + 1, longitude);
                vertex(pose, vertices, radius, interior, alpha, latitude + 1, longitude + 1);
                vertex(pose, vertices, radius, interior, alpha, latitude, longitude + 1);
            }
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            double radius,
            boolean interior,
            int alpha,
            int latitude,
            int longitude) {
        var point = POINTS[latitude][longitude];
        var vertex = vertices.addVertex(
                pose, (float) (point.x * radius), (float) (point.y * radius), (float) (point.z * radius));
        if (interior)
            vertex.setUv((float) longitude / LONGITUDES, 1f - (float) latitude / LATITUDES)
                    .setColor(CHANNEL, CHANNEL, CHANNEL, alpha);
        else vertex.setColor(0xFF080911);
    }
}
