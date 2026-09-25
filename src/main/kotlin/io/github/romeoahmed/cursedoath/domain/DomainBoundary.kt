package io.github.romeoahmed.cursedoath.domain

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** Sphere containment and crossings shared by domain targeting, movement, and attacks. */
object DomainBoundary {
    private const val MIN_MOTION = 1.0e-12
    private const val SURFACE_EPSILON = 1.0e-6

    fun crossing(
        start: Vec3,
        end: Vec3,
        center: Vec3,
        radius: Double,
    ): Double? {
        val offset = start.subtract(center)
        val motion = end.subtract(start)
        val a = motion.lengthSqr()
        if (a < MIN_MOTION) return null
        val b = offset.dot(motion)
        val c = offset.lengthSqr() - radius * radius
        val discriminant = b * b - a * c
        if (discriminant <= MIN_MOTION) return null
        if (kotlin.math.abs(c) <= SURFACE_EPSILON) return 0.0
        val root = sqrt(discriminant)
        val entry = (-b - root) / a
        val exit = (-b + root) / a
        return when {
            entry > SURFACE_EPSILON && entry <= 1.0 -> entry
            exit > SURFACE_EPSILON && exit <= 1.0 -> exit
            else -> null
        }
    }

    fun contains(
        point: Vec3,
        center: Vec3,
        radius: Double,
    ): Boolean = point.distanceToSqr(center) < radius * radius
}
