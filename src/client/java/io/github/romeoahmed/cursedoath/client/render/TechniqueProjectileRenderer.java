package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.romeoahmed.cursedoath.client.render.limitless.LimitlessEffects;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.List;
import net.minecraft.client.Minecraft;
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
        public List<LimitlessEffects.Form> forms = List.of();
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        return entity.getInterpolatedBoundingBox(partialTicks).inflate(LimitlessEffects.VISUAL_RADIUS);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @SuppressWarnings("ReferenceEquality") // First-person presentation belongs to the live local player.
    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.technique = entity.technique();
        var motion = entity.getDeltaMovement();
        state.direction = motion.equals(Vec3.ZERO) ? entity.getLookAngle() : motion.normalize();
        double launchDistance = TechniqueTuning.launchDistance(state.technique);
        if (launchDistance == 0) {
            state.forms = List.of();
            return;
        }
        var position = new Vec3(state.x, state.y, state.z);
        double traveled = position.distanceTo(entity.launchPosition());
        var client = Minecraft.getInstance();
        var owner = entity.getOwner();
        if (owner != null
                && owner == client.player
                && owner == client.getCameraEntity()
                && client.options.getCameraType().isFirstPerson()
                && (state.technique == Technique.PURPLE || motion.lengthSqr() > 0)) {
            var eye = owner.getEyePosition(partialTicks);
            double forward = position.subtract(eye).dot(state.direction) - launchDistance;
            // Preparation follows the local player; tracked motion arrives behind that view.
            // Hand off at the same forward plane instead of exposing the delayed spawn behind it.
            if (forward < 0) {
                position = position.subtract(state.direction.scale(forward));
                state.x = position.x;
                state.y = position.y;
                state.z = position.z;
            }
            traveled = Math.min(traveled, Math.max(0, forward));
        }
        state.forms = LimitlessEffects.flight(state.technique, state.ageInTicks, state.direction, traveled);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.forms.isEmpty()) LimitlessEffects.submit(poseStack, collector, state.forms);
        else {
            var technique = state.technique;
            var direction = state.direction;
            int detail = TechniqueGeometry.detail(state.distanceToCameraSq);
            float opacity = (float)
                    Math.clamp((Math.sqrt(state.distanceToCameraSq) - NEAR_DISTANCE) / FADE_DISTANCE, MIN_OPACITY, 1);
            collector.submitCustomGeometry(
                    poseStack,
                    EffectRenderTypes.CORE,
                    (pose, vertices) -> new TechniqueGeometry(new EffectMesh(pose, vertices, opacity, detail))
                            .draw(technique, 0, direction));
        }
        super.submit(state, poseStack, collector, camera);
    }
}
