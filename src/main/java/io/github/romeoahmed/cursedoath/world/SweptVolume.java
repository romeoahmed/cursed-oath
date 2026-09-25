package io.github.romeoahmed.cursedoath.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A sphere sweep or conservative oriented box sweep; bounds are only the broad phase.
public final class SweptVolume {
    private final Vec3 start, end, halfSize, forward, side, up, destination;
    private final @Nullable SphereSweep sphere;
    private final AABB bounds;

    public SweptVolume(Vec3 start, Vec3 end, Vec3 halfSize) {
        this(start, end, halfSize, false);
    }

    public SweptVolume(Vec3 start, Vec3 end, Vec3 halfSize, boolean rounded) {
        this.start = start;
        this.end = end;
        this.halfSize = halfSize;
        var direction = end.subtract(start).normalize();
        forward = direction.equals(Vec3.ZERO) ? new Vec3(0, 0, 1) : direction;
        var reference = Math.abs(forward.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        side = forward.cross(reference).normalize();
        up = side.cross(forward).normalize();
        destination = new Vec3(0, 0, end.distanceTo(start));
        sphere = rounded ? new SphereSweep(start, end, halfSize.x) : null;
        bounds = rounded
                ? new AABB(start, end).inflate(halfSize.x)
                : new AABB(start, end)
                        .inflate(
                                Math.abs(side.x) * halfSize.x
                                        + Math.abs(up.x) * halfSize.y
                                        + Math.abs(forward.x) * halfSize.z,
                                Math.abs(side.y) * halfSize.x
                                        + Math.abs(up.y) * halfSize.y
                                        + Math.abs(forward.y) * halfSize.z,
                                Math.abs(side.z) * halfSize.x
                                        + Math.abs(up.z) * halfSize.y
                                        + Math.abs(forward.z) * halfSize.z);
    }

    public Vec3 start() {
        return start;
    }

    public Vec3 end() {
        return end;
    }

    public Vec3 forward() {
        return forward;
    }

    public AABB bounds() {
        return bounds;
    }

    public @Nullable Double entry(AABB box) {
        return entry(box, Vec3.ZERO);
    }

    public @Nullable Double entry(AABB box, Vec3 movement) {
        if (sphere != null)
            return (movement.equals(Vec3.ZERO) ? sphere : new SphereSweep(start.add(movement), end, halfSize.x))
                    .entry(box);
        var center = local(box.getCenter().subtract(start));
        var half = new Vec3(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2);
        var from = local(movement);
        var extent = new Vec3(projectedSize(side, half), projectedSize(up, half), projectedSize(forward, half))
                .add(halfSize);
        var localBox = new AABB(center.subtract(extent), center.add(extent));
        if (localBox.contains(from)) return 0.0;
        var hit = localBox.clip(from, destination).orElse(null);
        return hit == null ? null : hit.distanceTo(from) / from.distanceTo(destination);
    }

    public @Nullable Vec3 contact(AABB box) {
        return contact(box, Vec3.ZERO);
    }

    public @Nullable Vec3 contact(AABB box, Vec3 movement) {
        var time = entry(box, movement);
        if (time == null) return null;
        var point = start.lerp(end, time).add(movement.scale(1 - time));
        return new Vec3(
                Math.clamp(point.x, box.minX, box.maxX),
                Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }

    public Vec3 source(Vec3 point) {
        return source(point, start);
    }
    /// Projects a cover-ray origin just behind the attack's original rear plane.
    ///
    /// @param point destination whose transverse position is preserved
    /// @param origin original attack center, retained across advancing segments
    /// @return ray origin aligned with the attack direction
    public Vec3 source(Vec3 point, Vec3 origin) {
        return point.subtract(forward.scale(point.subtract(origin).dot(forward) + halfSize.z + 0.001));
    }

    private Vec3 local(Vec3 vector) {
        return new Vec3(vector.dot(side), vector.dot(up), vector.dot(forward));
    }

    private static double projectedSize(Vec3 axis, Vec3 half) {
        return Math.abs(axis.x) * half.x + Math.abs(axis.y) * half.y + Math.abs(axis.z) * half.z;
    }
}
