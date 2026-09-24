package io.github.romeoahmed.cursedoath.world

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** Exact sphere/AABB contact: between box-face crossings, squared distance is quadratic. */
internal class SphereSweep(
    private val start: Vec3,
    end: Vec3,
    radius: Double,
) {
    private val path = AABB(start, end)
    private val motion = end.subtract(start)
    private val radiusSquared = radius * radius

    fun entry(box: AABB): Double? {
        if (box.distanceToSqr(path) > radiusSquared) return null
        if (box.distanceToSqr(start) <= radiusSquared) return 0.0
        val times = crossings(box)
        return (0 until times.lastIndex).firstNotNullOfOrNull { contact(box, times[it], times[it + 1]) }
    }

    private fun crossings(box: AABB): DoubleArray {
        val times = DoubleArray(MAX_CROSSINGS) { 1.0 }
        times[0] = 0.0
        var count = 2
        for (axis in Direction.Axis.entries) {
            val speed = motion.get(axis)
            if (speed == 0.0) continue
            for (side in 0..1) {
                val face = if (side == 0) box.min(axis) else box.max(axis)
                val time = (face - start.get(axis)) / speed
                if (time > 0.0 && time < 1.0) times[count++] = time
            }
        }
        times.sort()
        return times
    }

    private companion object {
        const val MAX_CROSSINGS = 8
    }

    private fun contact(
        box: AABB,
        low: Double,
        high: Double,
    ): Double? {
        val at = start.add(motion.scale(low))
        val c = box.distanceToSqr(at) - radiusSquared
        if (c <= 0.0) return low
        val middle = start.add(motion.scale((low + high) / 2))
        var a = 0.0
        var b = 0.0
        for (axis in Direction.Axis.entries) {
            val coordinate = middle.get(axis)
            if (coordinate >= box.min(axis) && coordinate <= box.max(axis)) continue
            val face = coordinate.coerceIn(box.min(axis), box.max(axis))
            val speed = motion.get(axis)
            a += speed * speed
            b += (at.get(axis) - face) * speed
        }
        val discriminant = b * b - a * c
        if (a == 0.0 || discriminant < 0.0) return null
        val offset = (-b - sqrt(discriminant)) / a
        return (low + offset).takeIf { offset >= 0.0 && offset <= high - low }
    }
}
