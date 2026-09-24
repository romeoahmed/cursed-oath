package io.github.romeoahmed.cursedoath.world

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** A sphere sweep or conservative oriented box sweep; bounds are only the broad phase. */
internal class SweptVolume(
    val start: Vec3,
    val end: Vec3,
    private val halfSize: Vec3,
    rounded: Boolean = false,
) {
    val forward = end.subtract(start).normalize()
    private val reference = if (abs(forward.y) < VERTICAL_THRESHOLD) Vec3(0.0, 1.0, 0.0) else Vec3(0.0, 0.0, 1.0)
    private val side = forward.cross(reference).normalize()
    private val up = side.cross(forward).normalize()
    private val destination = Vec3(0.0, 0.0, end.distanceTo(start))
    private val sphere = if (rounded) SphereSweep(start, end, halfSize.x) else null
    val bounds: AABB =
        if (rounded) {
            AABB(start, end).inflate(halfSize.x)
        } else {
            AABB(start, end).inflate(
                abs(side.x) * halfSize.x + abs(up.x) * halfSize.y + abs(forward.x) * halfSize.z,
                abs(side.y) * halfSize.x + abs(up.y) * halfSize.y + abs(forward.y) * halfSize.z,
                abs(side.z) * halfSize.x + abs(up.z) * halfSize.y + abs(forward.z) * halfSize.z,
            )
        }

    fun entry(
        box: AABB,
        movement: Vec3 = Vec3.ZERO,
    ): Double? {
        if (sphere != null) {
            val sweep = if (movement == Vec3.ZERO) sphere else SphereSweep(start.add(movement), end, halfSize.x)
            return sweep.entry(box)
        }
        val center = local(box.center.subtract(start))
        val half = Vec3(box.xsize / 2, box.ysize / 2, box.zsize / 2)
        val motion = local(movement)
        val extent =
            Vec3(
                projectedSize(side, half),
                projectedSize(up, half),
                projectedSize(forward, half),
            ).add(halfSize)
        val localBox = AABB(center.subtract(extent), center.add(extent))
        val from = motion
        return if (localBox.contains(from)) {
            0.0
        } else {
            localBox.clip(from, destination).orElse(null)?.let {
                it.distanceTo(from) /
                    from.distanceTo(destination)
            }
        }
    }

    fun contact(
        box: AABB,
        movement: Vec3 = Vec3.ZERO,
    ): Vec3? {
        val time = entry(box, movement) ?: return null
        val point = start.lerp(end, time).add(movement.scale(1 - time))
        return Vec3(
            point.x.coerceIn(box.minX, box.maxX),
            point.y.coerceIn(box.minY, box.maxY),
            point.z.coerceIn(box.minZ, box.maxZ),
        )
    }

    /** Parallel cover rays start behind the original attack, never inside a wall it has passed. */
    fun source(
        point: Vec3,
        origin: Vec3 = start,
    ): Vec3 = point.subtract(forward.scale(point.subtract(origin).dot(forward) + halfSize.z + SURFACE_MARGIN))

    private fun local(vector: Vec3) = Vec3(vector.dot(side), vector.dot(up), vector.dot(forward))

    private fun projectedSize(
        axis: Vec3,
        half: Vec3,
    ): Double = abs(axis.x) * half.x + abs(axis.y) * half.y + abs(axis.z) * half.z

    private companion object {
        const val VERTICAL_THRESHOLD = 0.99
        const val SURFACE_MARGIN = 0.001
    }
}
