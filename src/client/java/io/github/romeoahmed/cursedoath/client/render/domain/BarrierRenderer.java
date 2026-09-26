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
    private static final double RADIUS = 3.0;
    private static final Edge[] EDGES = edges();

    private record Edge(Vec3 start, Vec3 end, Vec3 width, float threshold) {}

    private static Edge[] edges() {
        var edges = new Edge[96];
        for (int i = 0; i < edges.length; i++) {
            double a = i * Math.TAU / edges.length, b = (i + 1) * Math.TAU / edges.length;
            edges[i] = new Edge(
                    new Vec3(Math.cos(a) * RADIUS, 0.035, Math.sin(a) * RADIUS),
                    new Vec3(Math.cos(b) * RADIUS, 0.035, Math.sin(b) * RADIUS),
                    new Vec3(Math.cos(a), 0, Math.sin(a)).scale(-0.045),
                    (i * 37 % 97) / 97f);
        }
        return edges;
    }

    private record State(Vec3 position, double height, double width, int strength, float age) {}

    private static final RenderStateDataKey<List<State>> KEY = RenderStateDataKey.create(() -> "cursed-oath:barriers");

    private BarrierRenderer() {}

    private static void simple(EffectMesh mesh, int strength, float age) {
        float integrity = strength / 100f;
        for (int i = 0; i < EDGES.length; i++) {
            var edge = EDGES[i];
            // Distributed gaps read as a peeling barrier, not a circular progress meter.
            if (edge.threshold() > integrity) continue;
            mesh.ribbon(edge.start(), edge.end(), edge.width(), 0xE3E7ED, 0.9f);
            if (integrity < 1 && edge.threshold() > integrity - 0.12) {
                double lift = 0.12 + 0.12 * Math.sin(age * 0.12 + i);
                mesh.slash(edge.start(), edge.end().add(0, lift, 0), edge.width(), 0xC3D1E5, 0.45f);
            }
        }
    }

    private static void amplification(EffectMesh mesh, State state) {
        double radius = state.width() * 0.5 + 0.08;
        for (int strand = 0; strand < 10; strand++) {
            double angle = strand * Math.TAU / 10;
            var points = new Vec3[9];
            for (int step = 0; step < points.length; step++) {
                double t = step / 8.0;
                double flow = angle + 0.12 * Math.sin(t * 8 - state.age() * 0.12 + strand);
                double shoulder = radius * (0.7 + 0.3 * Math.sin(t * Math.PI));
                points[step] =
                        new Vec3(Math.cos(flow) * shoulder, t * (state.height() + 0.08), Math.sin(flow) * shoulder);
            }
            var width = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.04);
            mesh.ribbon(points, width, 0xCBD0DD, 0.3f);
        }
    }

    public static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var states = new ArrayList<State>();
            for (var player : context.level().players()) {
                int strength = BarrierState.get(player);
                if (strength != 0)
                    states.add(new State(
                            player.getPosition(partial),
                            player.getBbHeight(),
                            player.getBbWidth(),
                            strength,
                            player.tickCount + partial));
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
                            if (state.strength() > 0) simple(mesh, state.strength(), state.age());
                            else if (camera.distanceToSqr(state.position().add(0, state.height() / 2, 0)) > 1)
                                amplification(mesh, state);
                        });
                pose.popPose();
            }
        });
    }
}
