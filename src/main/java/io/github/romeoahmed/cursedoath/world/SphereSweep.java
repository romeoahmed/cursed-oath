package io.github.romeoahmed.cursedoath.world;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Continuous sphere/AABB contact with a reusable query buffer; instances are not thread-safe.
public final class SphereSweep {
    private final Vec3 start;
    private final Vec3 end;
    private final AABB path;
    private final Vec3 motion;
    private final double radiusSquared;
    // Squared distance is quadratic between box-face crossings; reuse their times for each query.
    private final double[] times = new double[8];

    public SphereSweep(Vec3 start, Vec3 end, double radius) {
        this.start = start;
        this.end = end;
        path = new AABB(start, end);
        motion = end.subtract(start);
        radiusSquared = radius * radius;
    }

    /// Finds first contact along the segment, including tangency and initial overlap.
    ///
    /// @param box stationary target bounds
    /// @return segment fraction in `[0, 1]`, or `null` when there is no contact
    public @Nullable Double entry(AABB box) {
        if (box.distanceToSqr(path) > radiusSquared) return null;
        if (box.distanceToSqr(start) <= radiusSquared) return 0.0;
        int count = crossings(box);
        for (int i = 0; i < count - 1; i++) {
            if (times[i] == times[i + 1]) continue;
            Double contact = contact(box, times[i], times[i + 1]);
            if (contact != null) return contact;
        }
        return box.distanceToSqr(end) <= radiusSquared ? 1.0 : null;
    }

    private int crossings(AABB box) {
        times[0] = 0;
        times[1] = 1;
        int count = 2;
        for (var axis : AXES) {
            double speed = motion.get(axis);
            if (speed == 0) continue;
            for (int side = 0; side < 2; side++) {
                double face = side == 0 ? box.min(axis) : box.max(axis);
                double time = (face - start.get(axis)) / speed;
                if (time > 0 && time < 1) times[count++] = time;
            }
        }
        Arrays.sort(times, 0, count);
        return count;
    }

    private @Nullable Double contact(AABB box, double low, double high) {
        double middle = (low + high) / 2;
        double a = 0, b = 0, distanceSquared = 0;
        for (var axis : AXES) {
            double origin = start.get(axis), speed = motion.get(axis), at = origin + speed * low;
            double distance = at - Math.clamp(at, box.min(axis), box.max(axis));
            distanceSquared += distance * distance;
            double coordinate = origin + speed * middle;
            if (coordinate >= box.min(axis) && coordinate <= box.max(axis)) continue;
            double face = Math.clamp(coordinate, box.min(axis), box.max(axis));
            a += speed * speed;
            b += (at - face) * speed;
        }
        double c = distanceSquared - radiusSquared;
        if (c <= 0) return low;
        double discriminant = b * b - a * c;
        if (a == 0 || discriminant < 0) return null;
        double offset = (-b - Math.sqrt(discriminant)) / a;
        return offset >= 0 && offset <= high - low ? low + offset : null;
    }

    private static final Direction.Axis[] AXES = Direction.Axis.values();
}
