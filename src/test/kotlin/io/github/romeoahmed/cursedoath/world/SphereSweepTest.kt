package io.github.romeoahmed.cursedoath.world

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SphereSweepTest {
    @Test
    fun `a sweep can check different boxes without retaining target state`() {
        val sweep = SphereSweep(Vec3.ZERO, Vec3(0.0, 0.0, 8.0), 1.0)
        val near = AABB(-0.5, -0.5, 3.0, 0.5, 0.5, 4.0)
        val far = near.move(0.0, 0.0, 2.0)
        assertEquals(0.25, sweep.entry(near))
        assertNull(sweep.entry(near.move(8.0, 0.0, 0.0)))
        assertEquals(0.5, sweep.entry(far))
        assertEquals(0.25, sweep.entry(near))
    }

    @Test
    fun `stationary spheres tangency and negative direction have bounded contact times`() {
        val box = AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0)
        assertEquals(0.0, SphereSweep(Vec3.ZERO, Vec3.ZERO, 1.0).entry(box))
        assertNull(SphereSweep(Vec3(4.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0), 1.0).entry(box))
        assertEquals(
            0.3,
            assertNotNull(SphereSweep(Vec3(5.0, 0.0, 0.0), Vec3(-5.0, 0.0, 0.0), 1.0).entry(box)),
            1e-10,
        )
        assertNotNull(SphereSweep(Vec3(-5.0, 2.0, 0.0), Vec3(5.0, 2.0, 0.0), 1.0).entry(box))
    }

    @Test
    fun `seeded sweeps agree with native point distance and translation`() {
        val random = Random(5427)
        val offset = Vec3(20_000_000.0, -32.0, -20_000_000.0)
        repeat(500) { _ ->
            val start = Vec3(random.nextDouble(-5.0, 5.0), random.nextDouble(-5.0, 5.0), -5.0)
            val end = Vec3(random.nextDouble(-5.0, 5.0), random.nextDouble(-5.0, 5.0), 5.0)
            val box = AABB(-1.0, -0.5, -1.5, 1.0, 0.5, 1.5)
            val radius = random.nextDouble(0.1, 2.0)
            val entry = SphereSweep(start, end, radius).entry(box)
            val samplesHit = (0..100).any { box.distanceToSqr(start.lerp(end, it / 100.0)) <= radius * radius }
            if (samplesHit) assertNotNull(entry, "A sampled contact must not be missed")
            val translated = SphereSweep(start.add(offset), end.add(offset), radius).entry(box.move(offset))
            if (entry == null) {
                assertNull(translated)
            } else {
                assertTrue(entry in 0.0..1.0)
                assertEquals(radius * radius, box.distanceToSqr(start.lerp(end, entry)), 1e-8)
                assertEquals(entry, assertNotNull(translated), 1e-7)
            }
        }
    }
}
