package io.github.romeoahmed.cursedoath.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.world.phys.Vec3

internal class TechniqueGeometry(
    pose: PoseStack.Pose,
    vertices: VertexConsumer,
    opacity: Float = 1f,
    detail: Int = 1,
) {
    private val mesh = EffectMesh(pose, vertices, opacity, detail)

    fun draw(
        technique: Technique,
        progress: Float,
        direction: Vec3,
    ) {
        when (technique) {
            Technique.DISMANTLE -> {
                StrikeGeometry(mesh).dismantle(direction, progress)
            }

            Technique.CLEAVE -> {
                StrikeGeometry(mesh).cleave(direction, progress)
            }

            else -> {
                mesh.sphere(Vec3.ZERO, HEAL_RADIUS + progress, technique.color, 1 - progress)
            }
        }
    }

    companion object {
        private const val HEAL_RADIUS = 0.6
        private const val DETAIL_DISTANCE_SQUARED = 48.0 * 48.0

        fun detail(distanceSquared: Double): Int = if (distanceSquared > DETAIL_DISTANCE_SQUARED) 2 else 1
    }
}
