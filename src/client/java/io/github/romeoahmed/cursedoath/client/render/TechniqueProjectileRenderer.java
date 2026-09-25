package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.romeoahmed.cursedoath.client.render.limitless.LimitlessEffects;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TechniqueProjectileRenderer<T extends TechniqueProjectile>
        extends EntityRenderer<T, TechniqueProjectileRenderer.State> {
    private static final double NEAR_DISTANCE = 2.5, FADE_DISTANCE = 3, MIN_OPACITY = 0.1;

    public TechniqueProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends EntityRenderState {
        Technique technique = Technique.PURPLE;
        Vec3 direction = Vec3.ZERO;
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        return entity.getInterpolatedBoundingBox(partialTicks).inflate(LimitlessEffects.VISUAL_RADIUS);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.technique = entity.technique();
        var motion = entity.getDeltaMovement();
        state.direction = motion.equals(Vec3.ZERO) ? entity.getLookAngle() : motion.normalize();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        var technique = state.technique;
        var direction = state.direction;
        float age = state.ageInTicks;
        int detail = TechniqueGeometry.detail(state.distanceToCameraSq);
        float opacity = (float)
                Math.clamp((Math.sqrt(state.distanceToCameraSq) - NEAR_DISTANCE) / FADE_DISTANCE, MIN_OPACITY, 1);
        var forms = LimitlessEffects.flight(technique, age, direction);
        if (!forms.isEmpty()) LimitlessEffects.submit(poseStack, collector, forms, opacity);
        else
            collector.submitCustomGeometry(
                    poseStack,
                    EffectRenderTypes.ADDITIVE,
                    (pose, vertices) ->
                            new TechniqueGeometry(pose, vertices, opacity, detail).draw(technique, 0, direction));
        super.submit(state, poseStack, collector, camera);
    }
}
