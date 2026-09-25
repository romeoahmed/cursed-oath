package io.github.romeoahmed.cursedoath.client.render.limitless

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Opaque energy volume with flat color cells and view-facing brightness. */
internal class EnergySurface(
    private val pose: PoseStack.Pose,
    private val vertices: VertexConsumer,
) {
    fun draw(form: LimitlessEffects.Form) {
        val view = viewFrom(pose, form.center)
        val cells = if (view.lengthSqr() > DETAIL_DISTANCE_SQUARED) coarse else fine
        val eye = view.normalize()
        val palette =
            when (form.technique) {
                Technique.BLUE -> BLUE
                Technique.RED -> RED
                else -> PURPLE
            }
        val colors =
            if (form.fusion > 0f) {
                IntArray(palette.size) { ARGB.srgbLerp(form.fusion, palette[it], PURPLE[it]) }
            } else {
                palette
            }
        // sin(time + space) reuses cached spatial phases across cells.
        val phase = form.age * FLOW_SPEED
        val sine = sin(phase)
        val cosine = cos(phase)
        for (cell in cells) {
            val facing = cell.normal.dot(eye).coerceAtLeast(0.0)
            val motion = sine * cell.cosine + cosine * cell.sine
            val tone = (facing * facing + motion * FLOW_CONTRAST + cell.grain).coerceIn(0.0, 1.0)
            val color = colors[(tone * COLOR_STEPS).toInt()]
            for (point in cell.corners) {
                vertices
                    .addVertex(
                        pose,
                        (form.center.x + point.x * form.radius).toFloat(),
                        (form.center.y + point.y * form.radius).toFloat(),
                        (form.center.z + point.z * form.radius).toFloat(),
                    ).setColor(color)
            }
        }
        if (form.technique == Technique.BLUE) {
            EnergyTrails(pose, vertices).blueRibbons(form, dark = true)
        }
    }

    private data class Cell(
        val corners: Array<Vec3>,
        val normal: Vec3,
        val sine: Double,
        val cosine: Double,
        val grain: Double,
    )

    companion object {
        private fun palette(
            low: Int,
            middle: Int,
            high: Int,
        ): IntArray =
            IntArray(COLOR_STEPS + 1) { index ->
                val band = index.toDouble() / COLOR_STEPS
                if (band < HOT_THRESHOLD) {
                    ARGB.srgbLerp((band / HOT_THRESHOLD).toFloat(), low, middle)
                } else {
                    ARGB.srgbLerp(((band - HOT_THRESHOLD) / (1 - HOT_THRESHOLD)).toFloat(), middle, high)
                }
            }

        fun viewFrom(
            pose: PoseStack.Pose,
            center: Vec3,
        ): Vec3 {
            val matrix = pose.pose()
            return Vec3(-matrix.m30().toDouble(), -matrix.m31().toDouble(), -matrix.m32().toDouble()).subtract(center)
        }

        private fun cells(resolution: Int): Array<Cell> =
            buildList {
                for (normal in AXES) {
                    val (right, up) = EffectMesh.basis(normal)

                    fun point(
                        x: Int,
                        y: Int,
                    ): Vec3 =
                        normal
                            .add(right.scale(x * 2.0 / resolution - 1))
                            .add(up.scale(y * 2.0 / resolution - 1))
                            .normalize()
                    for (y in 0..<resolution) {
                        for (x in 0..<resolution) {
                            val corners = arrayOf(point(x, y), point(x + 1, y), point(x + 1, y + 1), point(x, y + 1))
                            val center = corners[0].add(corners[2]).normalize()
                            val phase = center.x * FLOW_SCALE + center.y * FLOW_TILT + center.z * FLOW_DEPTH
                            val grain = sin(x * GRAIN_X + y * GRAIN_Y + normal.x) * GRAIN_STRENGTH
                            add(Cell(corners, center, sin(phase), cos(phase), grain))
                        }
                    }
                }
            }.toTypedArray()

        private const val FLOW_SPEED = 0.28
        private const val FLOW_SCALE = 13.0
        private const val FLOW_TILT = 9.0
        private const val FLOW_DEPTH = 17.0
        private const val FLOW_CONTRAST = 0.12
        private const val GRAIN_STRENGTH = 0.02
        private const val GRAIN_X = 12.9898
        private const val GRAIN_Y = 78.233
        private const val COLOR_STEPS = 8
        private const val HOT_THRESHOLD = 0.6
        private const val DETAIL_DISTANCE_SQUARED = 48.0 * 48.0
        private val BLUE = palette(0xFF064BCD.toInt(), 0xFF26DFFF.toInt(), 0xFFF1FFFF.toInt())
        private val RED = palette(0xFFCA0925.toInt(), 0xFFFF485C.toInt(), 0xFFFFF9F1.toInt())
        private val PURPLE = palette(0xFF6D14BA.toInt(), 0xFFCD5CFF.toInt(), 0xFFFFEEFF.toInt())
        private val AXES =
            listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
                .flatMap { listOf(it, it.reverse()) }
        private val fine = cells(32)
        private val coarse = cells(16)
    }
}
