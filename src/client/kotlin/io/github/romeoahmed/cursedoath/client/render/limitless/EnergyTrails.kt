package io.github.romeoahmed.cursedoath.client.render.limitless

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Surface flows, soft glow, and directional trails around the opaque body. */
internal class EnergyTrails(
    private val pose: PoseStack.Pose,
    private val vertices: VertexConsumer,
    private val opacity: Float = 1f,
) {
    private val mesh = EffectMesh(pose, vertices, opacity, maxAlpha = 255)

    fun draw(form: LimitlessEffects.Form) {
        glow(form)
        when (form.technique) {
            Technique.BLUE -> {
                blueRibbons(form)
            }

            Technique.RED -> {
                redRays(form)
            }

            Technique.PURPLE -> {
                purpleFlow(form)
                if (form.flying) wake(form)
            }

            else -> {}
        }
    }

    private fun glow(form: LimitlessEffects.Form) {
        val view = EnergySurface.viewFrom(pose, form.center).normalize()
        val (right, up) = EffectMesh.basis(view)
        // Keep the billboard in front of the whole body; intersecting it cuts a dark disc into the glow.
        val center = form.center.add(view.scale(form.radius * GLOW_PLANE))
        val color = ARGB.srgbLerp(form.fusion, form.technique.color, Technique.PURPLE.color)
        val strength = 1 - form.fusion / 2

        fun vertex(
            point: Vec3,
            radius: Double,
            color: Int,
        ) {
            val x = point.x * radius
            val y = point.y * radius
            vertices
                .addVertex(
                    pose,
                    (center.x + right.x * x + up.x * y).toFloat(),
                    (center.y + right.y * x + up.y * y).toFloat(),
                    (center.z + right.z * x + up.z * y).toFloat(),
                ).setColor(color)
        }
        for (band in 0..<GLOW_BANDS) {
            val inner = band.toDouble() / GLOW_BANDS
            val outer = (band + 1.0) / GLOW_BANDS
            val innerRadius = form.radius * GLOW_RADIUS * inner
            val outerRadius = form.radius * GLOW_RADIUS * outer
            val innerColor = ARGB.color(((1 - inner) * (1 - inner) * opacity * GLOW_ALPHA * strength).toInt(), color)
            val outerColor = ARGB.color(((1 - outer) * (1 - outer) * opacity * GLOW_ALPHA * strength).toInt(), color)
            for (i in 0..<GLOW_SEGMENTS) {
                vertex(circle[i], innerRadius, innerColor)
                vertex(circle[i + 1], innerRadius, innerColor)
                vertex(circle[i + 1], outerRadius, outerColor)
                vertex(circle[i], outerRadius, outerColor)
            }
        }
    }

    fun blueRibbons(
        form: LimitlessEffects.Form,
        dark: Boolean = false,
    ) {
        for (i in 0..<BLUE_RIBBONS) {
            val seed = i * GOLDEN_ANGLE
            val index = i * NORMAL_COUNT / BLUE_RIBBONS
            val axis = normals[index]
            val (right, up) = axes[index]
            val phase = seed - form.age * BLUE_SPEED

            fun point(t: Double): Vec3 {
                val angle = phase + t * BLUE_SWEEP
                val radius = form.radius * (SURFACE_OFFSET + BLUE_REACH * (1 - t) * (1 - t))
                return form.center
                    .add(right.scale(cos(angle) * radius))
                    .add(up.scale(sin(angle) * radius))
                    .add(axis.scale(form.radius * BLUE_TWIST * sin(t * PI)))
            }
            for (step in 0..<RIBBON_STEPS) {
                val t = step.toDouble() / RIBBON_STEPS
                val width =
                    axis.scale(form.radius * (if (dark) DARK_WIDTH else LIGHT_WIDTH) * sin(t * PI) * (1 - form.fusion))
                val a = point(t)
                val b = point((step + 1.0) / RIBBON_STEPS)
                mesh.ribbon(a, b, width, if (dark) BLUE_INK else BLUE_LIGHT)
            }
        }
    }

    private fun redRays(form: LimitlessEffects.Form) {
        for (i in 0..<RED_RAYS) {
            val index = i * NORMAL_COUNT / RED_RAYS
            val direction = normals[index]
            val phase = (form.age * RED_SPEED + i.toDouble() / RED_RAYS) % 1.0
            val reach = form.radius * (RAY_START + phase * RAY_TRAVEL)
            val width =
                axes[index].first.scale(form.radius * RAY_WIDTH * (1 - phase) * (1 - form.fusion))
            val a = form.center.add(direction.scale(reach))
            val b = form.center.add(direction.scale(reach + form.radius * RAY_LENGTH))
            mesh.slash(a, b, width, RED_LIGHT, (1 - phase).toFloat())
        }
    }

    private fun purpleFlow(form: LimitlessEffects.Form) {
        for (i in 0..<PURPLE_FLOWS) {
            val index = i * NORMAL_COUNT / PURPLE_FLOWS
            val axis = normals[index]
            val (right, up) = axes[index]
            val phase = i * GOLDEN_ANGLE + form.age * PURPLE_SPEED
            for (step in 0..<RIBBON_STEPS) {
                val t = step.toDouble() / RIBBON_STEPS
                val angle = phase + t * PURPLE_SWEEP
                val radius = form.radius * (SURFACE_OFFSET + FLOW_RIPPLE * (1 + sin(angle * RIPPLE_FREQUENCY)))
                val a = form.center.add(right.scale(cos(angle) * radius)).add(up.scale(sin(angle) * radius))
                val next = angle + PURPLE_SWEEP / RIBBON_STEPS
                val b = form.center.add(right.scale(cos(next) * radius)).add(up.scale(sin(next) * radius))
                val width = axis.scale(form.radius * PURPLE_WIDTH * sin(t * PI))
                mesh.ribbon(a, b, width, if (i % 2 == 0) PURPLE_LIGHT else form.technique.color, FLOW_ALPHA)
            }
        }
    }

    private fun wake(form: LimitlessEffects.Form) {
        val (right, up) = EffectMesh.basis(form.direction)
        for (i in 0..<WAKE_STREAMS) {
            val phase = (form.age * WAKE_SPEED + i.toDouble() / WAKE_STREAMS) % 1.0
            val angle = i * GOLDEN_ANGLE
            val radial = right.scale(cos(angle)).add(up.scale(sin(angle)))
            val start =
                form.center
                    .add(radial.scale(form.radius * WAKE_RADIUS))
                    .subtract(form.direction.scale(form.radius * (WAKE_START + phase)))
            val end =
                start
                    .subtract(form.direction.scale(form.radius * WAKE_LENGTH))
                    .add(radial.scale(form.radius * WAKE_SPREAD))
            mesh.slash(
                start,
                end,
                radial.scale(form.radius * WAKE_WIDTH),
                PURPLE_LIGHT,
                ((1 - phase) * FLOW_ALPHA).toFloat(),
            )
        }
    }

    fun impact(progress: Float) {
        val radius = sqrt(progress.toDouble()) * TechniqueTuning.RED_RADIUS
        val alpha = (1 - progress) * (1 - progress)
        for ((index, normal) in normals.withIndex()) {
            val width = axes[index].first.scale(IMPACT_WIDTH * alpha)
            mesh.slash(normal.scale(radius * IMPACT_INNER), normal.scale(radius), width, RED_LIGHT, alpha)
        }
    }

    private companion object {
        const val GLOW_PLANE = 1.01
        const val GLOW_RADIUS = 1.5
        const val GLOW_ALPHA = 100
        const val GLOW_BANDS = 6
        const val GLOW_SEGMENTS = 32
        const val BLUE_RIBBONS = 5
        const val RIBBON_STEPS = 10
        const val BLUE_SPEED = 0.09
        const val BLUE_SWEEP = 2.2
        const val BLUE_REACH = 0.3
        const val BLUE_TWIST = 0.18
        const val SURFACE_OFFSET = 1.015
        const val DARK_WIDTH = 0.18
        const val LIGHT_WIDTH = 0.035
        const val BLUE_INK = 0x062C85
        const val BLUE_LIGHT = 0x24CCFF
        const val RED_RAYS = 12
        const val RED_SPEED = 0.11
        const val RAY_START = 1.1
        const val RAY_TRAVEL = 2.0
        const val RAY_LENGTH = 1.8
        const val RAY_WIDTH = 0.075
        const val RED_LIGHT = 0xFF2948
        const val PURPLE_FLOWS = 7
        const val PURPLE_SPEED = 0.07
        const val PURPLE_SWEEP = 2.0
        const val FLOW_RIPPLE = 0.035
        const val RIPPLE_FREQUENCY = 5
        const val PURPLE_WIDTH = 0.04
        const val PURPLE_LIGHT = 0xDC79FF
        const val FLOW_ALPHA = 0.55f
        const val WAKE_STREAMS = 12
        const val WAKE_SPEED = 0.08
        const val WAKE_RADIUS = 0.7
        const val WAKE_START = 0.45
        const val WAKE_LENGTH = 0.45
        const val WAKE_SPREAD = 0.12
        const val WAKE_WIDTH = 0.035
        const val IMPACT_WIDTH = 0.3
        const val IMPACT_INNER = 0.6
        const val GOLDEN_ANGLE = 2.399963
        const val NORMAL_COUNT = 24
        val normals =
            Array(NORMAL_COUNT) { i ->
                val y = 1 - 2 * (i + 0.5) / NORMAL_COUNT
                val radius = sqrt(1 - y * y)
                Vec3(cos(i * GOLDEN_ANGLE) * radius, y, sin(i * GOLDEN_ANGLE) * radius)
            }
        val axes = Array(NORMAL_COUNT) { EffectMesh.basis(normals[it]) }
        val circle =
            Array(GLOW_SEGMENTS + 1) { i ->
                val angle = i * 2 * PI / GLOW_SEGMENTS
                Vec3(cos(angle), sin(angle), 0.0)
            }
    }
}
