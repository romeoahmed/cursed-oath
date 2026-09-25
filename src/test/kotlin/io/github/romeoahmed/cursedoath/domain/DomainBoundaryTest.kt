package io.github.romeoahmed.cursedoath.domain

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainBoundaryTest {
    @Test
    fun `a fast segment cannot tunnel through both sides of a closed domain`() {
        assertEquals(0.25, DomainBoundary.crossing(Vec3(-20.0, 0.0, 0.0), Vec3(20.0, 0.0, 0.0), Vec3.ZERO, 10.0))
        assertEquals(0.5, DomainBoundary.crossing(Vec3.ZERO, Vec3(20.0, 0.0, 0.0), Vec3.ZERO, 10.0))
        assertNull(DomainBoundary.crossing(Vec3.ZERO, Vec3(2.0, 0.0, 0.0), Vec3.ZERO, 10.0))
        assertNull(DomainBoundary.crossing(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 10.0))
    }

    @Test
    fun `tangency is not a crossing and starting on the surface cannot tunnel out`() {
        assertNull(DomainBoundary.crossing(Vec3(-20.0, 10.0, 0.0), Vec3(20.0, 10.0, 0.0), Vec3.ZERO, 10.0))
        assertEquals(0.0, DomainBoundary.crossing(Vec3(10.0, 0.0, 0.0), Vec3(12.0, 0.0, 0.0), Vec3.ZERO, 10.0))
    }

    @Test
    fun `the boundary works vertically and at distant world coordinates`() {
        val center = Vec3(20_000_000.0, 100.0, -20_000_000.0)
        assertEquals(0.5, DomainBoundary.crossing(center, center.add(0.0, 48.0, 0.0), center, 24.0))
        assertNull(DomainBoundary.crossing(center.add(30.0, 0.0, 0.0), center.add(30.0, 20.0, 0.0), center, 24.0))
    }
}
