package io.github.romeoahmed.cursedoath.client.render.domain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes;
import io.github.romeoahmed.cursedoath.domain.DomainEntity;
import io.github.romeoahmed.cursedoath.domain.DomainIndex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

public final class DomainRenderer extends EntityRenderer<DomainEntity, DomainRenderer.State> {
    private static final float RISE_TICKS = 24f;
    private static final double RISE_DEPTH = 4.0;
    private static final double SHRINE_OFFSET = 11.0;
    private static final double SHRINE_CULL_RADIUS = 22.0;
    private final ShrineModel shrine = ShrineModel.load();

    public DomainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends EntityRenderState {
        double radius;
        boolean closed;

        public double radius() {
            return radius;
        }

        public boolean closed() {
            return closed;
        }

        float elapsed, yaw;
        boolean subdued;
        boolean interiorVisible;
        List<DomainClash.Neighbor> neighbors = List.of();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(DomainEntity entity, float partialTicks) {
        return entity.bounds().inflate(entity.closed() ? 0 : Math.max(0, SHRINE_CULL_RADIUS - entity.radius()));
    }

    @Override
    public void extractRenderState(DomainEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.subdued = Minecraft.getInstance().options.hideLightningFlash().get();
        state.radius = entity.radius();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        state.interiorVisible = true;
        var neighbors = new ArrayList<DomainClash.Neighbor>();
        for (var other : DomainIndex.inLevel(entity.level())) {
            if (other.equals(entity) || other.isRemoved()) continue;
            if (entity.closed() && other.closed() && other.getId() < entity.getId() && other.contains(camera))
                state.interiorVisible = false;
            double reach = entity.radius() + other.radius();
            if (entity.position().distanceToSqr(other.position()) < reach * reach)
                neighbors.add(new DomainClash.Neighbor(
                        other.position().subtract(entity.position()), other.radius(), entity.getId() < other.getId()));
        }
        state.neighbors = List.copyOf(neighbors);
        state.closed = entity.closed();
        state.yaw = entity.getYRot();
        state.elapsed = (float) (entity.level().getGameTime() - entity.started()) + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        double radius = state.radius;
        boolean closed = state.closed;
        float elapsed = state.elapsed;
        boolean subdued = state.subdued;
        var neighbors = state.neighbors;
        var eye = camera.pos.subtract(state.x, state.y, state.z);
        boolean inside = eye.lengthSqr() < radius * radius;
        if (closed && inside && !state.interiorVisible) return;
        poseStack.pushPose();
        if (closed && inside) poseStack.translate(eye.x, eye.y, eye.z);
        poseStack.rotateDegrees(Axis.YP, -state.yaw);
        if (!closed) {
            float progress = Math.clamp(elapsed / RISE_TICKS, 0f, 1f), remaining = 1 - progress;
            poseStack.translate(0, -RISE_DEPTH * remaining * remaining * remaining, -SHRINE_OFFSET);
        }
        collector.submitCustomGeometry(
                poseStack, closed && inside ? EffectRenderTypes.VOID : EffectRenderTypes.SOLID, (pose, vertices) -> {
                    if (closed) DomainSphere.draw(pose, vertices, inside ? radius * 2 : radius, elapsed, inside);
                    else shrine.draw(new EffectMesh(pose, vertices, 1, 1, 255));
                });
        if (closed && inside && elapsed < VoidOpening.DURATION) {
            int backdrop = VoidOpening.backdrop(elapsed, subdued);
            if ((backdrop >>> 24) != 0)
                collector.submitCustomGeometry(
                        poseStack,
                        EffectRenderTypes.CORE,
                        (pose, vertices) -> DomainSphere.backdrop(pose, vertices, radius * 1.8, backdrop));
            collector.submitCustomGeometry(
                    poseStack,
                    EffectRenderTypes.ADDITIVE,
                    (pose, vertices) -> VoidOpening.draw(pose, vertices, radius * 1.5, elapsed, subdued));
        }
        poseStack.popPose();
        if (!closed)
            collector.submitCustomGeometry(poseStack, EffectRenderTypes.CORE, (pose, vertices) -> {
                var mesh = new EffectMesh(pose, vertices);
                new ShrineSlashes(mesh, radius, elapsed, eye, subdued, neighbors).draw();
            });
        if (!neighbors.isEmpty())
            collector.submitCustomGeometry(poseStack, EffectRenderTypes.CORE, (pose, vertices) -> {
                var mesh = new EffectMesh(pose, vertices);
                for (var other : neighbors) DomainClash.draw(mesh, radius, other, elapsed, eye, subdued);
            });
        super.submit(state, poseStack, collector, camera);
    }
}
