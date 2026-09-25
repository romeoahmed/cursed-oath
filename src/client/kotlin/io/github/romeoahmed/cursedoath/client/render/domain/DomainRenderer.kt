package io.github.romeoahmed.cursedoath.client.render.domain

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes
import io.github.romeoahmed.cursedoath.domain.DomainEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class DomainRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<DomainEntity, DomainRenderer.State>(context) {
    private val shrine = ShrineModel.load()

    class State : EntityRenderState() {
        var radius = 0.0
        var closed = false
        var elapsed = 0f
        var yaw = 0f
        var subdued = false
    }

    override fun createRenderState() = State()

    override fun getBoundingBoxForCulling(
        entity: DomainEntity,
        partialTicks: Float,
    ): AABB = entity.bounds.inflate(if (entity.closed) 0.0 else (SHRINE_CULL_RADIUS - entity.radius).coerceAtLeast(0.0))

    override fun extractRenderState(
        entity: DomainEntity,
        state: State,
        partialTicks: Float,
    ) {
        super.extractRenderState(entity, state, partialTicks)
        state.subdued =
            Minecraft
                .getInstance()
                .options
                .hideLightningFlash()
                .get()
        state.radius = entity.radius
        state.closed = entity.closed
        state.yaw = entity.yRot
        state.elapsed = (entity.level().gameTime - entity.started).toFloat() + partialTicks
    }

    override fun submit(
        state: State,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        val radius = state.radius
        val closed = state.closed
        val elapsed = state.elapsed
        val subdued = state.subdued
        val eye = camera.pos.subtract(state.x, state.y, state.z)
        val inside = eye.lengthSqr() < radius * radius
        poseStack.pushPose()
        if (closed && inside) {
            poseStack.translate(eye.x, eye.y, eye.z)
        }
        poseStack.rotateDegrees(Axis.YP, -state.yaw)
        if (!closed) {
            val progress = (elapsed / RISE_TICKS).coerceIn(0f, 1f)
            val remaining = 1 - progress
            poseStack.translate(0.0, -RISE_DEPTH * remaining * remaining * remaining, -SHRINE_OFFSET)
        }
        collector.submitCustomGeometry(
            poseStack,
            if (closed && inside) {
                EffectRenderTypes.void
            } else {
                EffectRenderTypes.solid
            },
        ) { pose, vertices ->
            val mesh = EffectMesh(pose, vertices, maxAlpha = 255)
            if (closed) {
                DomainSphere.draw(pose, vertices, if (inside) radius * 2 else radius, elapsed, inside)
            } else {
                shrine.draw(mesh)
            }
        }
        poseStack.popPose()
        if (!closed) {
            collector.submitCustomGeometry(poseStack, EffectRenderTypes.additive) { pose, vertices ->
                val mesh = EffectMesh(pose, vertices)
                mesh.ring(Vec3.ZERO, GROUND_AXES, radius, SHRINE_COLOR, BAND_ALPHA)
                ShrineSlashes(mesh, radius, elapsed, eye, subdued).draw()
            }
        }
        super.submit(state, poseStack, collector, camera)
    }

    private companion object {
        const val RISE_TICKS = 24f
        const val RISE_DEPTH = 4.0
        const val SHRINE_OFFSET = 11.0
        const val SHRINE_CULL_RADIUS = 22.0
        const val BAND_ALPHA = 0.18f
        const val SHRINE_COLOR = 0xCE5045
        val GROUND_AXES = Vec3(1.0, 0.0, 0.0) to Vec3(0.0, 0.0, 1.0)
    }
}
