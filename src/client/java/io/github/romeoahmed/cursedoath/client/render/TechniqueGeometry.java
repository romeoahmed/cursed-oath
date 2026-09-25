package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.minecraft.world.phys.Vec3;

public final class TechniqueGeometry {
    private static final double HEAL_RADIUS = 0.6, DETAIL_DISTANCE_SQUARED = 48.0 * 48.0;
    private final EffectMesh mesh;

    public TechniqueGeometry(PoseStack.Pose pose, VertexConsumer vertices, float opacity, int detail) {
        mesh = new EffectMesh(pose, vertices, opacity, detail);
    }

    public void draw(Technique technique, float progress, Vec3 direction) {
        switch (technique) {
            case DISMANTLE -> new StrikeGeometry(mesh).dismantle(direction, progress);
            case CLEAVE -> new StrikeGeometry(mesh).cleave(direction, progress);
            default -> mesh.sphere(Vec3.ZERO, HEAL_RADIUS + progress, technique.color(), 1 - progress);
        }
    }

    public static int detail(double distanceSquared) {
        return distanceSquared > DETAIL_DISTANCE_SQUARED ? 2 : 1;
    }
}
