package io.github.romeoahmed.cursedoath.client.render.domain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes;
import io.github.romeoahmed.cursedoath.domain.DomainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DomainRenderer extends EntityRenderer<DomainEntity, DomainRenderer.State> {
    private static final float RISE_TICKS = 24f;
    private static final double RISE_DEPTH = 4.0;
    private static final double SHRINE_OFFSET = 11.0;
    private static final double SHRINE_CULL_RADIUS = 22.0;
    private static final float BAND_ALPHA = 0.18f;
    private static final int SHRINE_COLOR = 0xCE5045;
    private static final EffectMesh.Basis GROUND_AXES = new EffectMesh.Basis(new Vec3(1, 0, 0), new Vec3(0, 0, 1));
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
        var eye = camera.pos.subtract(state.x, state.y, state.z);
        boolean inside = eye.lengthSqr() < radius * radius;
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
        poseStack.popPose();
        if (!closed)
            collector.submitCustomGeometry(poseStack, EffectRenderTypes.ADDITIVE, (pose, vertices) -> {
                var mesh = new EffectMesh(pose, vertices);
                mesh.ring(Vec3.ZERO, GROUND_AXES, radius, SHRINE_COLOR, BAND_ALPHA);
                new ShrineSlashes(mesh, radius, elapsed, eye, subdued).draw();
            });
        super.submit(state, poseStack, collector, camera);
    }
}
