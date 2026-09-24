package io.github.romeoahmed.cursedoath.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class TechniqueGeometry(
    pose: PoseStack.Pose,
    vertices: VertexConsumer,
    opacity: Float = 1f,
) {
    private val mesh = TechniqueMesh(pose, vertices, opacity)

    fun draw(
        technique: Technique,
        age: Float,
        progress: Float,
        direction: Vec3,
    ) {
        when (technique) {
            Technique.BLUE -> blue(age, progress, direction)
            Technique.RED -> red(progress, direction)
            Technique.PURPLE -> purple(age, direction)
            Technique.DISMANTLE -> ShrineGeometry(mesh).dismantle(direction, progress)
            Technique.CLEAVE -> ShrineGeometry(mesh).cleave(direction, progress)
            else -> mesh.sphere(Vec3.ZERO, CORE_RADIUS + progress, technique.color, 1 - progress)
        }
    }

    fun orb(direction: Vec3) {
        mesh.sphere(Vec3.ZERO, CORE_RADIUS, Technique.RED.color)
        mesh.ring(Vec3.ZERO, TechniqueMesh.basis(direction), CORE_RADIUS * 2, Technique.RED.color)
        mesh.ribbon(
            arrayOf(direction.scale(-TAIL_SPACING), Vec3.ZERO),
            TechniqueMesh.basis(direction).second.scale(TRAIL_WIDTH),
            Technique.RED.color,
            TRAIL_ALPHA,
        )
    }

    fun orbCore() = mesh.sphere(Vec3.ZERO, CORE_RADIUS, RED_DARK, solid = true)

    fun core(
        technique: Technique,
        progress: Float,
    ) {
        val fade = (1 - progress).coerceIn(0f, 1f)
        when (technique) {
            Technique.BLUE -> {
                mesh.sphere(
                    Vec3.ZERO,
                    BLUE_CORE * CORE_INSET,
                    BLUE_DARK,
                    minOf(1f, fade * FADE_SPEED),
                    solid = true,
                )
            }

            Technique.RED -> {
                mesh.sphere(Vec3.ZERO, CORE_RADIUS + fade * fade, RED_DARK, fade, solid = true)
            }

            Technique.PURPLE -> {
                mesh.sphere(Vec3.ZERO, PURPLE_CORE * CORE_INSET, PURPLE_DARK, solid = true)
            }

            else -> {}
        }
    }

    fun charge(
        technique: Technique,
        progress: Float,
        direction: Vec3,
    ) {
        if (technique != Technique.PURPLE) {
            val radius = CHARGE_RADIUS * progress
            mesh.sphere(Vec3.ZERO, radius, technique.color)
            mesh.ring(Vec3.ZERO, TechniqueMesh.basis(direction), radius * 2, technique.color)
            return
        }
        val (side, up) = TechniqueMesh.basis(direction)
        val contact = TechniqueTuning.FUSION_CONTACT
        if (progress < contact) {
            val approach = progress / contact
            val separation = CHARGE_SEPARATION * (1 - approach * approach)
            val offset = side.scale(separation).add(up.scale(sin(approach * PI) * CHARGE_ARC))
            mesh.sphere(offset, CHARGE_RADIUS, Technique.BLUE.color)
            mesh.ring(offset, side to up, CHARGE_RADIUS * 2, Technique.BLUE.color)
            mesh.sphere(offset.reverse(), CHARGE_RADIUS, Technique.RED.color)
            mesh.ring(offset.reverse(), side to up, CHARGE_RADIUS * 2, Technique.RED.color)
        } else {
            val formed = (progress - contact) / (1 - contact)
            mesh.sphere(Vec3.ZERO, CHARGE_RADIUS + formed * FUSION_GROWTH, Technique.PURPLE.color)
            mesh.ring(Vec3.ZERO, side to up, CHARGE_RADIUS + formed * FUSION_RING, WHITE, 1 - formed)
        }
    }

    private fun blue(
        age: Float,
        progress: Float,
        direction: Vec3,
    ) {
        val fade = minOf(1f, (1 - progress) * FADE_SPEED)
        val axes = TechniqueMesh.basis(direction)
        mesh.ring(Vec3.ZERO, axes, BLUE_CORE, Technique.BLUE.color, fade)
        for (arm in 0..<ARMS) {
            val phase = (age * INFALL_SPEED + arm.toDouble() / ARMS) % 1.0
            val radius = BLUE_CORE + (1 - phase) * TechniqueTuning.BLUE_EXCAVATION
            val angle = arm * 2 * PI / ARMS + age * ORBIT_SPEED
            val points =
                Array(POINTS) { i ->
                    val t = i.toDouble() / (POINTS - 1)
                    val r = radius * (1 - t) + BLUE_CORE * t
                    val turn = angle + t * PI
                    axes.first
                        .scale(cos(turn) * r)
                        .add(axes.second.scale(sin(turn) * r))
                        .add(direction.scale(sin(t * PI) * (1 - t)))
                }
            mesh.ribbon(points, axes.second.scale(TRAIL_WIDTH), Technique.BLUE.color, fade * TRAIL_ALPHA)
        }
        for (layer in 0..<LAYERS) {
            val phase = (age * INFALL_SPEED + layer.toDouble() / LAYERS) % 1.0
            mesh.ring(Vec3.ZERO, axes, BLUE_CORE + (1 - phase) * BLUE_CORE, CYAN, (phase * fade).toFloat())
        }
    }

    private fun red(
        progress: Float,
        direction: Vec3,
    ) {
        val axes = TechniqueMesh.basis(direction)
        val (side, up) = axes
        val fade = (1 - progress) * (1 - progress)
        val expansion = sqrt(progress.toDouble())
        mesh.sphere(Vec3.ZERO, CORE_RADIUS + fade, Technique.RED.color, fade * TRAIL_ALPHA)
        for (layer in 0..<LAYERS) {
            val radius = CORE_RADIUS + expansion * (TechniqueTuning.RED_RADIUS - layer)
            mesh.ring(direction.scale(expansion * layer), axes, radius, Technique.RED.color, fade * TRAIL_ALPHA)
        }
        for (i in 0..<ARMS * 2) {
            val angle = i * PI / ARMS
            val ray = side.scale(cos(angle)).add(up.scale(sin(angle))).add(direction.scale(CORE_RADIUS))
            val start = ray.scale(expansion * TechniqueTuning.RED_RADIUS)
            mesh.ribbon(
                arrayOf(start, start.scale(RAY_STRETCH)),
                up.scale(TRAIL_WIDTH),
                Technique.RED.color,
                fade * TRAIL_ALPHA,
            )
        }
    }

    private fun purple(
        age: Float,
        direction: Vec3,
    ) {
        val (side, up) = TechniqueMesh.basis(direction)
        mesh.sphere(Vec3.ZERO, PURPLE_CORE, Technique.PURPLE.color, TRAIL_ALPHA)
        for (i in 0..<ARMS) {
            val latitude = (i + 0.5) * PI / ARMS
            val longitude = i * GOLDEN_ANGLE + age * ORBIT_SPEED
            val normal =
                side
                    .scale(cos(longitude) * sin(latitude))
                    .add(up.scale(cos(latitude)))
                    .add(direction.scale(sin(longitude) * sin(latitude)))
            val (across, along) = TechniqueMesh.basis(normal)
            val points =
                Array(POINTS) { j ->
                    val angle = j.toDouble() / (POINTS - 1) * ARC_LENGTH + age * ORBIT_SPEED
                    val radius = TechniqueTuning.PURPLE_RADIUS + sin(j * RIPPLE_FREQUENCY + age) * RIPPLE_SIZE
                    across.scale(cos(angle) * radius).add(along.scale(sin(angle) * radius))
                }
            mesh.ribbon(points, normal.scale(TRAIL_WIDTH), Technique.PURPLE.color, TRAIL_ALPHA)
        }
        for (i in 1..LAYERS) {
            mesh.ring(
                direction.scale(-i * TAIL_SPACING),
                side to up,
                TechniqueTuning.PURPLE_RADIUS - i * CORE_RADIUS,
                Technique.PURPLE.color,
                TRAIL_ALPHA / i,
            )
        }
    }

    private companion object {
        const val GOLDEN_ANGLE = 2.4
        const val ARC_LENGTH = 1.7
        const val RIPPLE_FREQUENCY = 1.3
        const val RIPPLE_SIZE = 0.12
        const val POINTS = 17
        const val ARMS = 8
        const val LAYERS = 3
        const val CORE_INSET = 0.92
        const val BLUE_DARK = 0x02091B
        const val RED_DARK = 0xBA050B
        const val PURPLE_DARK = 0x290345
        const val WHITE = 0xF4EAFF
        const val CYAN = 0x66DDFF
        const val CORE_RADIUS = 0.6
        const val BLUE_CORE = 1.35
        const val CHARGE_RADIUS = 0.4
        const val CHARGE_SEPARATION = 2.2
        const val CHARGE_ARC = 0.65
        const val FUSION_GROWTH = 1.4
        const val FUSION_RING = 4.0
        const val FADE_SPEED = 6
        const val ORBIT_SPEED = 0.08
        const val INFALL_SPEED = 0.055
        const val TRAIL_WIDTH = 0.1
        const val TRAIL_ALPHA = 0.65f
        const val PURPLE_CORE = 4.0
        const val RAY_STRETCH = 1.5
        const val TAIL_SPACING = 2.5
    }
}
