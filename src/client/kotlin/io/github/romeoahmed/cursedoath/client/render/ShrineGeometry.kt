package io.github.romeoahmed.cursedoath.client.render

import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.sin

internal class ShrineGeometry(
    private val mesh: TechniqueMesh,
) {
    fun dismantle(
        direction: Vec3,
        progress: Float,
    ) {
        val (side, up) = TechniqueMesh.basis(direction)
        val span = TechniqueTuning.DISMANTLE_WIDTH
        val points =
            Array(POINTS) { i ->
                val t = i.toDouble() / (POINTS - 1)
                side.scale((t * 2 - 1) * span).add(direction.scale(sin(t * PI) * BLADE_BOW))
            }
        mesh.ribbon(points, up.scale(EDGE_WIDTH), WHITE, 1 - progress)
        mesh.ribbon(points, direction.scale(-WAKE_LENGTH).add(up.scale(EDGE_WIDTH)), PALE, WAKE_ALPHA * (1 - progress))
    }

    fun cleave(
        direction: Vec3,
        progress: Float,
    ) {
        val (side, up) = TechniqueMesh.basis(direction)
        val extent = TechniqueTuning.CLEAVE_EXTENT
        val fade = (1 - progress) * (1 - progress)
        for (line in -GRID..GRID) {
            val offset = line * SPACING
            val reveal = (progress * REVEAL_SPEED).coerceAtMost(1f)
            val span = extent * reveal
            val a = side.scale(offset).add(up.scale(-span))
            val b = side.scale(offset).add(up.scale(span))
            mesh.ribbon(arrayOf(a, b), side.scale(EDGE_WIDTH), WHITE, fade)
            val c = up.scale(offset).add(side.scale(-span))
            val d = up.scale(offset).add(side.scale(span))
            mesh.ribbon(arrayOf(c, d), up.scale(EDGE_WIDTH), WHITE, fade)
        }
    }

    fun blackFlash(
        direction: Vec3,
        progress: Float,
    ) {
        val (side, up) = TechniqueMesh.basis(direction)
        for (i in -GRID..GRID) {
            val end = side.scale(i.toDouble()).add(up.scale(if (i % 2 == 0) FLASH_EXTENT else -FLASH_EXTENT))
            mesh.ribbon(
                arrayOf(Vec3.ZERO, end.scale(BEND).add(side), end),
                up.scale(EDGE_WIDTH * 2),
                CRIMSON,
                1 - progress,
            )
        }
    }

    private companion object {
        const val POINTS = 17
        const val GRID = TechniqueTuning.CLEAVE_GRID
        const val SPACING = TechniqueTuning.CLEAVE_SPACING
        const val EDGE_WIDTH = TechniqueTuning.CLEAVE_THICKNESS
        const val BLADE_BOW = 1.5
        const val WAKE_LENGTH = 1.2
        const val WAKE_ALPHA = 0.18f
        const val REVEAL_SPEED = 5
        const val FLASH_EXTENT = 2.0
        const val BEND = 0.45
        const val WHITE = 0xF2F6FF
        const val PALE = 0x9BAABD
        const val CRIMSON = 0xBA0827
    }
}
