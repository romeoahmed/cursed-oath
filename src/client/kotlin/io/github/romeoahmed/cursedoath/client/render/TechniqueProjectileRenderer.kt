package io.github.romeoahmed.cursedoath.client.render

import com.mojang.blaze3d.vertex.PoseStack
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class TechniqueProjectileRenderer<T : TechniqueProjectile>(
    context: EntityRendererProvider.Context,
) : EntityRenderer<T, TechniqueProjectileRenderer.State>(context) {
    class State : EntityRenderState() {
        var technique = Technique.PURPLE
        var direction = Vec3.ZERO
    }

    override fun getBoundingBoxForCulling(
        entity: T,
        partialTicks: Float,
    ): AABB = entity.getInterpolatedBoundingBox(partialTicks).inflate(VISUAL_RADIUS)

    override fun createRenderState() = State()

    override fun extractRenderState(
        entity: T,
        state: State,
        partialTicks: Float,
    ) {
        super.extractRenderState(entity, state, partialTicks)
        state.technique = entity.technique
        state.direction = if (entity.deltaMovement == Vec3.ZERO) entity.lookAngle else entity.deltaMovement.normalize()
    }

    override fun submit(
        state: State,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        val technique = state.technique
        val direction = state.direction
        val age = state.ageInTicks
        val detail = TechniqueGeometry.detail(state.distanceToCameraSq)
        val progress = if (technique == Technique.BLUE) (age / TechniqueTuning.BLUE_DURATION).coerceIn(0f, 1f) else 0f
        val opacity =
            ((sqrt(state.distanceToCameraSq) - NEAR_DISTANCE) / FADE_DISTANCE)
                .coerceIn(MIN_OPACITY, 1.0)
                .toFloat()
        collector.submitCustomGeometry(poseStack, TechniqueRenderTypes.additive) { pose, vertices ->
            val geometry = TechniqueGeometry(pose, vertices, opacity, detail)
            if (technique == Technique.RED) {
                geometry.orb(direction)
            } else {
                geometry.draw(technique, age, progress, direction)
            }
        }
        if (technique != Technique.DISMANTLE) {
            collector.submitCustomGeometry(poseStack, TechniqueRenderTypes.core) { pose, vertices ->
                val geometry = TechniqueGeometry(pose, vertices, opacity, detail)
                if (technique == Technique.RED) geometry.orbCore() else geometry.core(technique, progress)
            }
        }
        super.submit(state, poseStack, collector, camera)
    }

    private companion object {
        // Includes Blue's outer spiral and Purple's rearmost ring, beyond the damage radius.
        const val VISUAL_RADIUS = 9.0
        const val NEAR_DISTANCE = 2.5
        const val FADE_DISTANCE = 3.0
        const val MIN_OPACITY = 0.1
    }
}
