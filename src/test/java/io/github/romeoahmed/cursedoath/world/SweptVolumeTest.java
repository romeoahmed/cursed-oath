package io.github.romeoahmed.cursedoath.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class SweptVolumeTest {
    @Test
    void aStationaryBoxRetainsItsExtentWithoutContactingUnrelatedTargets() {
        var center = new Vec3(4.0, 2.0, -3.0);
        var volume = new SweptVolume(center, center, new Vec3(2.0, 0.5, 0.25));
        assertEquals(AABB.ofSize(center, 4.0, 1.0, 0.5), volume.bounds());
        assertEquals(0.0, volume.entry(AABB.ofSize(center, 0.1, 0.1, 0.1)));
        assertNull(volume.entry(AABB.ofSize(center.add(0.0, 2.0, 0.0), 0.1, 0.1, 0.1)));
    }

    @Test
    void horizontalCutIsWideButDoesNotReachTheLayerAbove() {
        var cut = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(2.5, 0.18, 0.18));
        assertNotNull(cut.entry(new AABB(2.0, -0.1, 4.0, 3.0, 0.1, 5.0)));
        assertNull(cut.entry(new AABB(0.0, 1.0, 4.0, 1.0, 2.0, 5.0)));
    }

    @Test
    void verticalCutRotatesItsThinAxisWithTheCastingDirection() {
        var cut = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 8.0, 0.0), new Vec3(2.5, 0.18, 0.18));
        assertNotNull(cut.entry(new AABB(2.0, 4.0, -0.1, 3.0, 5.0, 0.1)));
        assertNull(cut.entry(new AABB(0.0, 4.0, 1.0, 1.0, 5.0, 2.0)));
    }

    @Test
    void relativeMotionCatchesATargetCrossingBetweenTickPositions() {
        var cut = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(0.2, 0.2, 0.2));
        var target = new AABB(2.0, -0.5, 4.0, 3.0, 0.5, 5.0);
        assertNull(cut.entry(target));
        assertNotNull(cut.entry(target, new Vec3(5.0, 0.0, 0.0)));
    }

    @Test
    void roundedSweepExcludesSquareCornersButAcceptsACrossingTarget() {
        var volume = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(2.0, 2.0, 2.0), true);
        assertNull(volume.entry(new AABB(1.8, 1.8, 3.0, 2.2, 2.2, 4.0)));
        assertNotNull(volume.entry(new AABB(1.8, -0.2, 3.0, 2.2, 0.2, 4.0)));
        assertNotNull(volume.entry(new AABB(4.0, -0.2, 3.0, 5.0, 0.2, 4.0), new Vec3(8.0, 0.0, 0.0)));
    }

    @Test
    void sphereCapsExcludeCylinderCornersAndReportNormalizedContactTime() {
        var volume = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(2.0, 2.0, 2.0), true);
        assertNull(volume.entry(new AABB(1.8, -0.1, 9.8, 2.0, 0.1, 10.0)));
        assertEquals(0.5, volume.entry(new AABB(-0.5, -0.5, 6.0, 0.5, 0.5, 7.0)));
    }

    @Test
    void sphereSweepsAreRotationIndependentAndDoNotCombineDifferentContactTimes() {
        var target = new AABB(-0.1, -0.1, 3.9, 0.1, 0.1, 4.1);
        var volume = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(0.2, 0.2, 0.2), true);
        assertNull(volume.entry(target, new Vec3(4.0, 0.0, 0.0)));
        for (var direction :
                List.of(new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(1.0, 1.0, 1.0).normalize())) {
            var sweep = new SweptVolume(Vec3.ZERO, direction.scale(8.0), new Vec3(2.0, 2.0, 2.0), true);
            assertNotNull(sweep.entry(AABB.ofSize(direction.scale(4.0), 0.2, 0.2, 0.2)));
            assertEquals(4.0, sweep.bounds().getXsize() - Math.abs(direction.x * 8.0), 1e-10);
        }
    }

    @Test
    void anOverlappingTargetIsContactedAtTheStart() {
        var volume = new SweptVolume(Vec3.ZERO, new Vec3(0.0, 0.0, 8.0), new Vec3(2.5, 2.5, 2.5));
        assertEquals(0.0, volume.entry(new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5)));
    }
}
