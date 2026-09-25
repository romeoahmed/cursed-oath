package io.github.romeoahmed.cursedoath.client.render.domain

import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes
import io.github.romeoahmed.cursedoath.domain.BarrierState
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object BarrierRenderer {
    private data class State(
        val position: Vec3,
        val height: Double,
        val strength: Int,
    )

    private val key = RenderStateDataKey.create<List<State>> { "cursed-oath:barriers" }
    private val axes = Vec3(1.0, 0.0, 0.0) to Vec3(0.0, 0.0, 1.0)
    private const val BOUNDARY_ALPHA = 0.25f
    private const val RADIUS = 3.0
    private const val SIMPLE_COLOR = 0xE2E9EC
    private const val AMPLIFICATION_COLOR = 0xB5C6D8
    private const val AURA_RADIUS = 0.65
    private const val AURA_ALPHA = 0.2f
    private const val GROUND_OFFSET = 0.025

    private fun simple(
        mesh: EffectMesh,
        strength: Int,
    ) {
        val center = Vec3(0.0, GROUND_OFFSET, 0.0)
        mesh.ring(center, axes, RADIUS, SIMPLE_COLOR, BOUNDARY_ALPHA)
        // A complete faint boundary preserves the actual radius as the bright segments erode.
        val segments = (strength * SEGMENTS / FULL_STRENGTH).coerceIn(0, SEGMENTS)
        repeat(segments) { index ->
            val angle = index * 2 * PI / SEGMENTS
            val end = angle + 2 * PI / SEGMENTS * SEGMENT_FILL
            val a = Vec3(cos(angle), 0.0, sin(angle)).scale(RADIUS)
            val b = Vec3(cos(end), 0.0, sin(end)).scale(RADIUS)
            mesh.ribbon(center.add(a), center.add(b), a.scale(-SEGMENT_WIDTH), SIMPLE_COLOR)
        }
    }

    private const val SEGMENTS = 24
    private const val FULL_STRENGTH = 100
    private const val SEGMENT_FILL = 0.8
    private const val SEGMENT_WIDTH = 0.04

    fun initialize() {
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            val partial = context.deltaTracker().getGameTimeDeltaPartialTick(false)
            context.levelState().setData(
                key,
                context.level().players().mapNotNull { player ->
                    val strength = BarrierState.get(player)
                    if (strength == 0) {
                        null
                    } else {
                        State(player.getPosition(partial), player.bbHeight.toDouble(), strength)
                    }
                },
            )
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            val cameraState = context.levelState().cameraRenderState
            val camera = cameraState.pos
            for (state in context.levelState().getData(key).orEmpty()) {
                val bounds = AABB(state.position, state.position.add(0.0, state.height, 0.0)).inflate(RADIUS)
                if (!cameraState.cullFrustum.isVisible(bounds)) continue
                val pose = context.poseStack()
                pose.pushPose()
                pose.translate(state.position.x - camera.x, state.position.y - camera.y, state.position.z - camera.z)
                context.submitNodeCollector().submitCustomGeometry(
                    pose,
                    EffectRenderTypes.additive,
                ) { matrix, vertices ->
                    val mesh = EffectMesh(matrix, vertices)
                    if (state.strength > 0) {
                        simple(mesh, state.strength)
                    } else {
                        mesh.sphere(Vec3(0.0, state.height / 2, 0.0), AURA_RADIUS, AMPLIFICATION_COLOR, AURA_ALPHA)
                    }
                }
                pose.popPose()
            }
        }
    }
}
