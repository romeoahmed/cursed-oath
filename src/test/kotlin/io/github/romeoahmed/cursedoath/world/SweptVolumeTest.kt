package io.github.romeoahmed.cursedoath.world

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SweptVolumeTest {
    @Test
    fun `a stationary box retains its extent without contacting unrelated targets`() {
        val center = Vec3(4.0, 2.0, -3.0)
        val volume = SweptVolume(center, center, Vec3(2.0, 0.5, 0.25))
        assertEquals(AABB.ofSize(center, 4.0, 1.0, 0.5), volume.bounds)
        assertEquals(0.0, volume.entry(AABB.ofSize(center, 0.1, 0.1, 0.1)))
        assertNull(volume.entry(AABB.ofSize(center.add(0.0, 2.0, 0.0), 0.1, 0.1, 0.1)))
    }

    @Test
    fun `horizontal cut is wide but does not reach the layer above`() {
        val cut = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(2.5, 0.18, 0.18))
        assertNotNull(cut.entry(AABB(2.0, -0.1, 4.0, 3.0, 0.1, 5.0)))
        assertNull(cut.entry(AABB(0.0, 1.0, 4.0, 1.0, 2.0, 5.0)))
    }

    @Test
    fun `vertical cut rotates its thin axis with the casting direction`() {
        val cut = SweptVolume(Vec3.ZERO, Vec3(0.0, 8.0, 0.0), Vec3(2.5, 0.18, 0.18))
        assertNotNull(cut.entry(AABB(2.0, 4.0, -0.1, 3.0, 5.0, 0.1)))
        assertNull(cut.entry(AABB(0.0, 4.0, 1.0, 1.0, 5.0, 2.0)))
    }

    @Test
    fun `relative motion catches a target crossing between tick positions`() {
        val cut = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(0.2, 0.2, 0.2))
        val target = AABB(2.0, -0.5, 4.0, 3.0, 0.5, 5.0)
        assertNull(cut.entry(target))
        assertNotNull(cut.entry(target, Vec3(5.0, 0.0, 0.0)))
    }

    @Test
    fun `rounded sweep excludes square corners but accepts a crossing target`() {
        val volume = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(2.0, 2.0, 2.0), rounded = true)
        assertNull(volume.entry(AABB(1.8, 1.8, 3.0, 2.2, 2.2, 4.0)))
        assertNotNull(volume.entry(AABB(1.8, -0.2, 3.0, 2.2, 0.2, 4.0)))
        assertNotNull(volume.entry(AABB(4.0, -0.2, 3.0, 5.0, 0.2, 4.0), Vec3(8.0, 0.0, 0.0)))
    }

    @Test
    fun `sphere caps exclude cylinder corners and report normalized contact time`() {
        val volume = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(2.0, 2.0, 2.0), rounded = true)
        assertNull(volume.entry(AABB(1.8, -0.1, 9.8, 2.0, 0.1, 10.0)))
        assertEquals(0.5, volume.entry(AABB(-0.5, -0.5, 6.0, 0.5, 0.5, 7.0)))
    }

    @Test
    fun `sphere sweeps are rotation independent and do not combine different contact times`() {
        val target = AABB(-0.1, -0.1, 3.9, 0.1, 0.1, 4.1)
        val volume = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(0.2, 0.2, 0.2), rounded = true)
        assertNull(volume.entry(target, Vec3(4.0, 0.0, 0.0)))
        for (direction in listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(1.0, 1.0, 1.0).normalize())) {
            val sweep = SweptVolume(Vec3.ZERO, direction.scale(8.0), Vec3(2.0, 2.0, 2.0), rounded = true)
            assertNotNull(sweep.entry(AABB.ofSize(direction.scale(4.0), 0.2, 0.2, 0.2)))
            assertEquals(4.0, sweep.bounds.xsize - kotlin.math.abs(direction.x * 8.0), 1e-10)
        }
    }

    @Test
    fun `lattice covers every line symmetrically and preserves gaps`() {
        val grid = CleaveLattice(Vec3.ZERO, Vec3(0.0, 0.0, 1.0))

        fun intersects(box: AABB) = grid.cuts.any { it.entry(box) != null }

        for (offset in -6..6 step 2) {
            assertTrue(intersects(AABB.ofSize(Vec3(offset.toDouble(), 1.0, 4.0), 0.1, 0.1, 0.1)))
            assertTrue(intersects(AABB.ofSize(Vec3(1.0, offset.toDouble(), 4.0), 0.1, 0.1, 0.1)))
        }
        assertTrue(!intersects(AABB.ofSize(Vec3(0.0, 0.0, 10.0), 0.1, 0.1, 0.1)))
        assertTrue(!intersects(AABB.ofSize(Vec3(1.0, 1.0, 4.0), 0.5, 0.5, 0.5)))
        assertTrue(!intersects(AABB.ofSize(Vec3(8.0, 0.0, 4.0), 0.5, 0.5, 0.5)))
    }

    @Test
    fun `an overlapping target is contacted at the start`() {
        val volume = SweptVolume(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), Vec3(2.5, 2.5, 2.5))
        assertEquals(0.0, volume.entry(AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5)))
    }
}
