package io.github.romeoahmed.cursedoath.client.render.domain

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object DomainSphere {
    private const val LONGITUDES = 64
    private const val LATITUDES = 32
    private const val CHANNEL = 255
    private const val REVEAL_TICKS = 24f
    private const val VIEW_LONGITUDE = 0.555
    private val points =
        Array(LATITUDES + 1) { latitude ->
            val pitch = -PI / 2 + latitude * PI / LATITUDES
            Array(LONGITUDES + 1) { longitude ->
                val yaw = (longitude.toDouble() / LONGITUDES - VIEW_LONGITUDE) * 2 * PI
                Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
            }
        }

    fun draw(
        pose: PoseStack.Pose,
        vertices: VertexConsumer,
        radius: Double,
        age: Float,
        interior: Boolean,
    ) {
        val alpha = ((age / REVEAL_TICKS).coerceIn(0f, 1f) * CHANNEL).toInt()

        fun vertex(
            latitude: Int,
            longitude: Int,
        ) {
            val point = points[latitude][longitude]
            val vertex =
                vertices.addVertex(
                    pose,
                    (point.x * radius).toFloat(),
                    (point.y * radius).toFloat(),
                    (point.z * radius).toFloat(),
                )
            if (interior) {
                vertex
                    .setUv(longitude.toFloat() / LONGITUDES, 1f - latitude.toFloat() / LATITUDES)
                    .setColor(CHANNEL, CHANNEL, CHANNEL, alpha)
            } else {
                vertex.setColor(0xFF080911.toInt())
            }
        }
        for (latitude in 0..<LATITUDES) {
            for (longitude in 0..<LONGITUDES) {
                vertex(latitude, longitude)
                vertex(latitude + 1, longitude)
                vertex(latitude + 1, longitude + 1)
                vertex(latitude, longitude + 1)
            }
        }
    }
}
