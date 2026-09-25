package io.github.romeoahmed.cursedoath.client.render.domain;

import io.github.romeoahmed.cursedoath.client.render.EffectMesh;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/// Decorative cuts stay anchored to domain-local cells as the camera crosses cell boundaries.
final class ShrineSlashes {
    private static final long BYTE_MASK = 255L;
    private static final double JITTER = 0.6;
    private static final double CELL_INSET = 0.2;
    private static final double CELL = 8.0;
    private static final int REACH = 4;
    private static final float PERIOD = 10f;
    private static final float LIFETIME = 4f;
    private static final float REVEAL = 0.65f;
    private static final double NEAR = 3.0;
    private static final double CLEAR = 7.0;
    private static final double FAR = 32.0;
    private static final double FADE = 8.0;
    private static final double WIDTH = 0.035;
    private static final int COLOR = 0xE6D7CF;
    private static final int ACCENT_EVERY = 7;
    private static final double ACCENT_LENGTH = 9.0;
    private static final double LENGTH = 5.0;
    private static final float SUBDUED_ALPHA = 0.35f;
    private static final double PHASE_STEP = 0.61803398875;
    private static final double TURN = 6.28318530718;
    private final EffectMesh mesh;
    private final double radius;
    private final float age;
    private final Vec3 eye;
    private final boolean subdued;

    ShrineSlashes(EffectMesh mesh, double radius, float age, Vec3 eye, boolean subdued) {
        this.mesh = mesh;
        this.radius = radius;
        this.age = age;
        this.eye = eye;
        this.subdued = subdued;
    }

    void draw() {
        int cx = (int) Math.floor(eye.x / CELL),
                cy = (int) Math.floor(eye.y / CELL),
                cz = (int) Math.floor(eye.z / CELL);
        for (int x = cx - REACH; x <= cx + REACH; x++)
            for (int y = cy - REACH; y <= cy + REACH; y++) for (int z = cz - REACH; z <= cz + REACH; z++) cut(x, y, z);
    }

    private void cut(int x, int y, int z) {
        long seed = HashCommon.mix(BlockPos.asLong(x, y, z));
        if (subdued && (seed & 1) == 0) return;
        float offset = (float) ((seed >>> 16) % 1000) / 1000 * PERIOD, clock = age + offset;
        if (clock % PERIOD >= LIFETIME) return;
        int beat = (int) Math.floor(clock / PERIOD);
        double phase = (double) (seed & 0xFFFF) / 65536 * TURN + beat * PHASE_STEP;
        double length = (seed + beat) % ACCENT_EVERY == 0 ? ACCENT_LENGTH : LENGTH;
        var center = new Vec3(
                (x + offset(seed >>> 24)) * CELL, (y + offset(seed >>> 32)) * CELL, (z + offset(seed >>> 40)) * CELL);
        if (eye.distanceToSqr(center) >= FAR * FAR || center.length() + length > radius) return;
        var direction = new Vec3(Math.cos(phase), Math.sin(phase * 2), Math.sin(phase)).normalize();
        var delta = direction.scale(length);
        var a = center.subtract(delta);
        var b = center.add(delta);
        float alpha = visibility(eye, a, b) * (subdued ? SUBDUED_ALPHA : 1);
        if (alpha <= 0) return;
        float progress = clock % PERIOD / LIFETIME;
        var tip = a.lerp(b, Math.min(clock % PERIOD / REVEAL, 1f));
        var width = direction.cross(eye.subtract(center)).normalize().scale(WIDTH);
        mesh.slash(a, tip, width, COLOR, alpha * (1 - progress));
    }

    private static float visibility(Vec3 eye, Vec3 a, Vec3 b) {
        var motion = b.subtract(a);
        double fraction = Math.clamp(eye.subtract(a).dot(motion) / motion.lengthSqr(), 0, 1),
                nearest = eye.distanceTo(a.lerp(b, fraction)),
                distance = eye.distanceTo(a.lerp(b, 0.5));
        return (float)
                (Math.clamp((nearest - NEAR) / (CLEAR - NEAR), 0, 1) * Math.clamp((FAR - distance) / FADE, 0, 1));
    }

    private static double offset(long seed) {
        return (double) (seed & BYTE_MASK) / BYTE_MASK * JITTER + CELL_INSET;
    }
}
