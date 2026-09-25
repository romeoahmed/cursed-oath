package io.github.romeoahmed.cursedoath.world

import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** The same finite cutting planes drive terrain, entity contact and the client grid. */
internal class CleaveLattice(
    val center: Vec3,
    direction: Vec3,
) {
    private val forward = direction.normalize()
    private val reference = if (abs(forward.y) < VERTICAL_THRESHOLD) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
    private val side = forward.cross(reference).normalize()
    private val up = side.cross(forward).normalize()
    val cuts: List<SweptVolume> =
        (-TechniqueTuning.CLEAVE_GRID..TechniqueTuning.CLEAVE_GRID).flatMap { line ->
            val offset = line * TechniqueTuning.CLEAVE_SPACING
            listOf(
                cut(center.add(side.scale(offset)), Vec3(THICKNESS, EXTENT, THICKNESS)),
                cut(center.add(up.scale(offset)), Vec3(EXTENT, THICKNESS, THICKNESS)),
            )
        }
    val bounds: AABB = cuts.map { it.bounds }.reduce(AABB::minmax)

    private fun cut(
        start: Vec3,
        size: Vec3,
    ) = SweptVolume(start, start.add(forward.scale(EXTENT)), size)

    private companion object {
        const val EXTENT = TechniqueTuning.CLEAVE_EXTENT
        const val THICKNESS = TechniqueTuning.CLEAVE_THICKNESS
        const val VERTICAL_THRESHOLD = 0.99
    }
}
