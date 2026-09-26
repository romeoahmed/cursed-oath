package io.github.romeoahmed.cursedoath.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class SphereSweepTest {
    @Test
    void aSweepCanCheckDifferentBoxesWithoutRetainingTargetState() {
        var sweep = new SphereSweep(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), 1.0);
        var near = new AABB(-0.5, -0.5, 3.0, 0.5, 0.5, 4.0);
        var far = near.move(0.0, 0.0, 2.0);
        assertEquals(0.25, sweep.entry(near));
        assertNull(sweep.entry(near.move(8.0, 0.0, 0.0)));
        assertEquals(0.5, sweep.entry(far));
        assertEquals(0.25, sweep.entry(near));
        assertNull(sweep.entry(near.move(8.0, 0.0, 0.0)));
        assertEquals(0.5, sweep.entry(far));
    }

    @Test
    void obliqueSweepsIncludeExactEndpointContactOnDifferentFaces() {
        var sweep = new SphereSweep(Vec3.ZERO, new Vec3(2.0, 3.0, 5.0), 6.0);
        for (var box : List.of(
                new AABB(1.0, 2.0, 11.0, 2.0, 3.0, 12.0),
                new AABB(8.0, 2.0, 4.0, 9.0, 3.0, 5.0),
                new AABB(8.0, 3.0, 4.0, 9.0, 4.0, 5.0))) {
            assertEquals(1.0, sweep.entry(box));
        }
        assertNull(sweep.entry(new AABB(8.001, 3.0, 4.0, 9.001, 4.0, 5.0)));
    }

    @Test
    void stationarySpheresTangencyAndNegativeDirectionHaveBoundedContactTimes() {
        var box = new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);
        assertEquals(0.0, new SphereSweep(Vec3.ZERO, Vec3.ZERO, 1.0).entry(box));
        assertNull(new SphereSweep(new Vec3(4.0, 0.0, 0.0), new Vec3(4.0, 0.0, 0.0), 1.0).entry(box));
        assertEquals(
                0.3,
                Objects.requireNonNull(
                        new SphereSweep(new Vec3(5.0, 0.0, 0.0), new Vec3(-5.0, 0.0, 0.0), 1.0).entry(box)),
                1e-10);
        assertNotNull(new SphereSweep(new Vec3(-5.0, 2.0, 0.0), new Vec3(5.0, 2.0, 0.0), 1.0).entry(box));
    }

    @Test
    void seededSweepsAgreeWithNativePointDistanceAndTranslation() {
        var random = new Random(5427);
        var offset = new Vec3(20_000_000.0, -32.0, -20_000_000.0);
        for (int i = 0; i < 500; i++) {
            var start = new Vec3(random.nextDouble(-5.0, 5.0), random.nextDouble(-5.0, 5.0), -5.0);
            var end = new Vec3(random.nextDouble(-5.0, 5.0), random.nextDouble(-5.0, 5.0), 5.0);
            var box = new AABB(-1.0, -0.5, -1.5, 1.0, 0.5, 1.5);
            var radius = random.nextDouble(0.1, 2.0);
            var entry = new SphereSweep(start, end, radius).entry(box);
            boolean samplesHit = false;
            for (int sample = 0; sample <= 100; sample++)
                if (box.distanceToSqr(start.lerp(end, sample / 100.0)) <= radius * radius) samplesHit = true;
            if (samplesHit) assertNotNull(entry, "A sampled contact must not be missed");
            var translated = new SphereSweep(start.add(offset), end.add(offset), radius).entry(box.move(offset));
            if (entry == null) {
                assertNull(translated);
            } else {
                assertTrue(entry >= 0 && entry <= 1);
                assertEquals(radius * radius, box.distanceToSqr(start.lerp(end, entry)), 1e-8);
                assertNotNull(translated);
                assertEquals(entry, translated, 1e-7);
            }
        }
    }
}
