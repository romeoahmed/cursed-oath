package io.github.romeoahmed.cursedoath.domain;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class DomainBoundaryTest {
    @Test
    void aFastSegmentCannotTunnelThroughBothSidesOfAClosedDomain() {
        assertEquals(
                0.25, DomainBoundary.crossing(new Vec3(-20.0, 0.0, 0.0), new Vec3(20.0, 0.0, 0.0), Vec3.ZERO, 10.0));
        assertEquals(0.5, DomainBoundary.crossing(Vec3.ZERO, new Vec3(20.0, 0.0, 0.0), Vec3.ZERO, 10.0));
        assertNull(DomainBoundary.crossing(Vec3.ZERO, new Vec3(2.0, 0.0, 0.0), Vec3.ZERO, 10.0));
        assertNull(DomainBoundary.crossing(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10.0));
    }

    @Test
    void tangencyIsNotACrossingAndStartingOnTheSurfaceCannotTunnelOut() {
        assertNull(DomainBoundary.crossing(new Vec3(-20.0, 10.0, 0.0), new Vec3(20.0, 10.0, 0.0), Vec3.ZERO, 10.0));
        assertEquals(0.0, DomainBoundary.crossing(new Vec3(10.0, 0.0, 0.0), new Vec3(12.0, 0.0, 0.0), Vec3.ZERO, 10.0));
    }

    @Test
    void theBoundaryWorksVerticallyAndAtDistantWorldCoordinates() {
        var center = new Vec3(20_000_000.0, 100.0, -20_000_000.0);
        assertEquals(0.5, DomainBoundary.crossing(center, center.add(0.0, 48.0, 0.0), center, 24.0));
        assertNull(DomainBoundary.crossing(center.add(30.0, 0.0, 0.0), center.add(30.0, 20.0, 0.0), center, 24.0));
    }
}
