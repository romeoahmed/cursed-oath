package io.github.romeoahmed.cursedoath.client.render.domain;

import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes;
import io.github.romeoahmed.cursedoath.domain.BarrierState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BarrierRenderer {
    private static final float BOUNDARY_ALPHA = 0.25f;
    private static final double RADIUS = 3.0;
    private static final int SIMPLE_COLOR = 0xE2E9EC;
    private static final int AMPLIFICATION_COLOR = 0xB5C6D8;
    private static final double AURA_RADIUS = 0.65;
    private static final float AURA_ALPHA = 0.2f;
    private static final double GROUND_OFFSET = 0.025;
    private static final int SEGMENTS = 24;
    private static final int FULL_STRENGTH = 100;
    private static final double SEGMENT_FILL = 0.8;
    private static final double SEGMENT_WIDTH = 0.04;

    private record State(Vec3 position, double height, int strength) {}

    private static final RenderStateDataKey<List<State>> KEY = RenderStateDataKey.create(() -> "cursed-oath:barriers");
    private static final EffectMesh.Basis AXES = new EffectMesh.Basis(new Vec3(1, 0, 0), new Vec3(0, 0, 1));

    private BarrierRenderer() {}

    private static void simple(EffectMesh mesh, int strength) {
        var center = new Vec3(0, GROUND_OFFSET, 0);
        mesh.ring(center, AXES, RADIUS, SIMPLE_COLOR, BOUNDARY_ALPHA);
        // The faint boundary preserves the radius as bright segments erode.
        int segments = Math.clamp(strength * SEGMENTS / FULL_STRENGTH, 0, SEGMENTS);
        for (int index = 0; index < segments; index++) {
            double angle = index * 2 * Math.PI / SEGMENTS, end = angle + 2 * Math.PI / SEGMENTS * SEGMENT_FILL;
            var a = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(RADIUS);
            var b = new Vec3(Math.cos(end), 0, Math.sin(end)).scale(RADIUS);
            mesh.ribbon(center.add(a), center.add(b), a.scale(-SEGMENT_WIDTH), SIMPLE_COLOR);
        }
    }

    public static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var states = new ArrayList<State>();
            for (var player : context.level().players()) {
                int strength = BarrierState.get(player);
                if (strength != 0) states.add(new State(player.getPosition(partial), player.getBbHeight(), strength));
            }
            context.levelState().setData(KEY, List.copyOf(states));
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            var cameraState = context.levelState().cameraRenderState;
            var camera = cameraState.pos;
            var states = context.levelState().getData(KEY);
            if (states == null) return;
            for (var state : states) {
                var bounds = new AABB(state.position(), state.position().add(0, state.height(), 0)).inflate(RADIUS);
                if (!cameraState.cullFrustum.isVisible(bounds)) continue;
                var pose = context.poseStack();
                pose.pushPose();
                pose.translate(
                        state.position().x - camera.x, state.position().y - camera.y, state.position().z - camera.z);
                context.submitNodeCollector()
                        .submitCustomGeometry(pose, EffectRenderTypes.ADDITIVE, (matrix, vertices) -> {
                            var mesh = new EffectMesh(matrix, vertices);
                            if (state.strength() > 0) simple(mesh, state.strength());
                            else
                                mesh.sphere(
                                        new Vec3(0, state.height() / 2, 0),
                                        AURA_RADIUS,
                                        AMPLIFICATION_COLOR,
                                        AURA_ALPHA);
                        });
                pose.popPose();
            }
        });
    }
}
