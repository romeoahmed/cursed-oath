package io.github.romeoahmed.cursedoath.domain;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Sphere containment and crossings shared by domain targeting, movement, and attacks.
public final class DomainBoundary {
    private DomainBoundary() {}

    private static final double MIN_MOTION = 1.0e-12;
    private static final double SURFACE_EPSILON = 1.0e-6;

    public static @Nullable Double crossing(Vec3 start, Vec3 end, Vec3 center, double radius) {
        var offset = start.subtract(center);
        var motion = end.subtract(start);
        double a = motion.lengthSqr();
        if (a < MIN_MOTION) return null;
        double b = offset.dot(motion), c = offset.lengthSqr() - radius * radius;
        double discriminant = b * b - a * c;
        if (discriminant <= MIN_MOTION) return null;
        if (Math.abs(c) <= SURFACE_EPSILON) return 0.0;
        double root = Math.sqrt(discriminant), entry = (-b - root) / a, exit = (-b + root) / a;
        if (entry > SURFACE_EPSILON && entry <= 1) return entry;
        return exit > SURFACE_EPSILON && exit <= 1 ? exit : null;
    }

    public static boolean contains(Vec3 point, Vec3 center, double radius) {
        return point.distanceToSqr(center) < radius * radius;
    }
}
