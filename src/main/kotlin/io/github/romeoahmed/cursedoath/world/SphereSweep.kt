package io.github.romeoahmed.cursedoath.world

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** Exact sphere/AABB contact: between box-face crossings, squared distance is quadratic. */
internal class SphereSweep(
    private val start: Vec3,
    private val end: Vec3,
    radius: Double,
) {
    private val path = AABB(start, end)
    private val motion = end.subtract(start)
    private val radiusSquared = radius * radius

    // Queries run sequentially on the owning server thread, including queued terrain segments.
    private val times = DoubleArray(MAX_CROSSINGS)

    fun entry(box: AABB): Double? {
        if (box.distanceToSqr(path) > radiusSquared) return null
        if (box.distanceToSqr(start) <= radiusSquared) return 0.0
        val count = crossings(box)
        return (0 until count - 1).firstNotNullOfOrNull {
            if (times[it] == times[it + 1]) null else contact(box, times[it], times[it + 1])
        } ?: 1.0.takeIf { box.distanceToSqr(end) <= radiusSquared }
    }

    private fun crossings(box: AABB): Int {
        times[0] = 0.0
        times[1] = 1.0
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
        times.sort(0, count)
        return count
    }

    private companion object {
        const val MAX_CROSSINGS = 8
    }

    private fun contact(
        box: AABB,
        low: Double,
        high: Double,
    ): Double? {
        val middle = (low + high) / 2
        var a = 0.0
        var b = 0.0
        var distanceSquared = 0.0
        for (axis in Direction.Axis.entries) {
            val origin = start.get(axis)
            val speed = motion.get(axis)
            val at = origin + speed * low
            val distance = at - at.coerceIn(box.min(axis), box.max(axis))
            distanceSquared += distance * distance
            val coordinate = origin + speed * middle
            if (coordinate >= box.min(axis) && coordinate <= box.max(axis)) continue
            val face = coordinate.coerceIn(box.min(axis), box.max(axis))
            a += speed * speed
            b += (at - face) * speed
        }
        val c = distanceSquared - radiusSquared
        if (c <= 0.0) return low
        val discriminant = b * b - a * c
        return if (a == 0.0 || discriminant < 0.0) {
            null
        } else {
            val offset = (-b - sqrt(discriminant)) / a
            (low + offset).takeIf { offset >= 0.0 && offset <= high - low }
        }
    }
}
