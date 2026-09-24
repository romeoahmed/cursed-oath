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
    private val box: AABB,
) {
    private val path = AABB(start, end)
    private val motion = end.subtract(start)
    private val radiusSquared = radius * radius

    fun entry(): Double? {
        if (box.distanceToSqr(path) > radiusSquared) return null
        if (box.distanceToSqr(start) <= radiusSquared) return 0.0
        val times = crossings()
        return (0 until times.lastIndex).firstNotNullOfOrNull { contact(times[it], times[it + 1]) }
    }

    private fun crossings(): List<Double> {
        val times = mutableListOf(0.0, 1.0)
        for (axis in Direction.Axis.entries) {
            val speed = motion.get(axis)
            if (speed == 0.0) continue
            for (face in listOf(box.min(axis), box.max(axis))) {
                val time = (face - start.get(axis)) / speed
                if (time > 0.0 && time < 1.0) times.add(time)
            }
        }
        times.sort()
        return times
    }

    private fun contact(
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
