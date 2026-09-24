package io.github.romeoahmed.cursedoath.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Cached unit geometry, submitted through Minecraft's render pipeline. */
internal class TechniqueMesh(
    private val pose: PoseStack.Pose,
    private val vertices: VertexConsumer,
    private val opacity: Float = 1f,
) {
    fun ring(
        center: Vec3,
        axes: Pair<Vec3, Vec3>,
        radius: Double,
        rgb: Int,
        alpha: Float = 1f,
    ) {
        val color = color(rgb, alpha)
        for (i in 0..<SEGMENTS) {
            ringVertex(center, axes, circle[i], radius * INNER_RADIUS, color)
            ringVertex(center, axes, circle[i], radius, color)
            ringVertex(center, axes, circle[i + 1], radius, color)
            ringVertex(center, axes, circle[i + 1], radius * INNER_RADIUS, color)
        }
    }

    fun sphere(
        center: Vec3,
        radius: Double,
        rgb: Int,
        alpha: Float = 1f,
        solid: Boolean = false,
    ) {
        val color = color(rgb, alpha * if (solid) 1f else CORE_ALPHA)
        for (lat in 0..<LATITUDES) {
            for (i in 0..<SEGMENTS) {
                sphereVertex(center, sphere[lat][i], radius, color)
                sphereVertex(center, sphere[lat + 1][i], radius, color)
                sphereVertex(center, sphere[lat + 1][i + 1], radius, color)
                sphereVertex(center, sphere[lat][i + 1], radius, color)
            }
        }
    }

    private fun sphereVertex(
        center: Vec3,
        point: Vec3,
        radius: Double,
        color: Int,
    ) {
        vertices
            .addVertex(
                pose,
                (center.x + point.x * radius).toFloat(),
                (center.y + point.y * radius).toFloat(),
                (center.z + point.z * radius).toFloat(),
            ).setColor(color)
    }

    private fun ringVertex(
        center: Vec3,
        axes: Pair<Vec3, Vec3>,
        point: Vec3,
        radius: Double,
        color: Int,
    ) {
        val (side, up) = axes
        val x = point.x * radius
        val y = point.y * radius
        vertices
            .addVertex(
                pose,
                (center.x + side.x * x + up.x * y).toFloat(),
                (center.y + side.y * x + up.y * y).toFloat(),
                (center.z + side.z * x + up.z * y).toFloat(),
            ).setColor(color)
    }

    fun ribbon(
        points: Array<Vec3>,
        width: Vec3,
        rgb: Int,
        alpha: Float = 1f,
    ) {
        val color = color(rgb, alpha)
        for (i in 0..<points.lastIndex) {
            quad(
                points[i],
                points[i].add(width),
                points[i + 1].add(width),
                points[i + 1],
                color,
            )
        }
    }

    private fun quad(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        d: Vec3,
        color: Int,
    ) {
        vertex(a, color)
        vertex(b, color)
        vertex(c, color)
        vertex(d, color)
    }

    private fun vertex(
        point: Vec3,
        color: Int,
    ) {
        vertices.addVertex(pose, point.x.toFloat(), point.y.toFloat(), point.z.toFloat()).setColor(color)
    }

    private fun color(
        rgb: Int,
        alpha: Float,
    ): Int = ((alpha.coerceIn(0f, 1f) * opacity * MAX_ALPHA).toInt() shl ALPHA_SHIFT) or rgb

    companion object {
        private const val SEGMENTS = 32
        private const val LATITUDES = 8
        private const val INNER_RADIUS = 0.94
        private const val MAX_ALPHA = 160
        private const val CORE_ALPHA = 0.55f
        private const val ALPHA_SHIFT = 24
        private const val VERTICAL_THRESHOLD = 0.99
        private val circle =
            Array(SEGMENTS + 1) { i -> Vec3(cos(i * 2 * PI / SEGMENTS), sin(i * 2 * PI / SEGMENTS), 0.0) }
        private val sphere =
            Array(LATITUDES + 1) { lat ->
                val angle = -PI / 2 + lat * PI / LATITUDES
                Array(SEGMENTS + 1) { i -> Vec3(circle[i].x * cos(angle), sin(angle), circle[i].y * cos(angle)) }
            }

        fun basis(direction: Vec3): Pair<Vec3, Vec3> {
            val forward = if (direction == Vec3.ZERO) Vec3(0.0, 0.0, 1.0) else direction
            val reference = if (abs(forward.y) < VERTICAL_THRESHOLD) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
            val side = forward.cross(reference).normalize()
            return side to side.cross(forward).normalize()
        }
    }
}
