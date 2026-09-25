package io.github.romeoahmed.cursedoath.client.render.domain

import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import it.unimi.dsi.fastutil.HashCommon
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Domain-local cells keep cuts anchored when the observer crosses a sampling boundary. */
internal class ShrineSlashes(
    private val mesh: EffectMesh,
    private val radius: Double,
    private val age: Float,
    private val eye: Vec3,
    private val subdued: Boolean,
) {
    fun draw() {
        val cx = floor(eye.x / CELL).toInt()
        val cy = floor(eye.y / CELL).toInt()
        val cz = floor(eye.z / CELL).toInt()
        for (x in cx - REACH..cx + REACH) {
            for (y in cy - REACH..cy + REACH) {
                for (z in cz - REACH..cz + REACH) {
                    cut(x, y, z)
                }
            }
        }
    }

    private fun cut(
        x: Int,
        y: Int,
        z: Int,
    ) {
        val seed = HashCommon.mix(BlockPos.asLong(x, y, z))
        if (subdued && seed and 1L == 0L) return
        val offset = ((seed ushr 16) % 1000).toFloat() / 1000 * PERIOD
        val clock = age + offset
        if (clock % PERIOD >= LIFETIME) return
        val beat = floor(clock / PERIOD).toInt()
        val phase = (seed and 0xFFFF).toDouble() / 65536 * TURN + beat * PHASE_STEP
        val length = if ((seed + beat) % ACCENT_EVERY == 0L) ACCENT_LENGTH else LENGTH
        val center =
            Vec3(
                (x + offset(seed ushr 24)) * CELL,
                (y + offset(seed ushr 32)) * CELL,
                (z + offset(seed ushr 40)) * CELL,
            )
        if (eye.distanceToSqr(center) >= FAR * FAR) return
        // Keep the complete cut inside the sure-hit volume, including its pointed tips.
        if (center.length() + length > radius) return
        val direction = Vec3(cos(phase), sin(phase * 2), sin(phase)).normalize()
        val delta = direction.scale(length)
        val a = center.subtract(delta)
        val b = center.add(delta)
        val alpha = visibility(eye, a, b) * if (subdued) SUBDUED_ALPHA else 1f
        if (alpha <= 0f) return
        val progress = clock % PERIOD / LIFETIME
        val tip = a.lerp(b, (clock % PERIOD / REVEAL).coerceAtMost(1f).toDouble())
        val width = direction.cross(eye.subtract(center)).normalize().scale(WIDTH)
        mesh.slash(a, tip, width, COLOR, alpha * (1 - progress))
    }

    private fun visibility(
        eye: Vec3,
        a: Vec3,
        b: Vec3,
    ): Float {
        val motion = b.subtract(a)
        val fraction = (eye.subtract(a).dot(motion) / motion.lengthSqr()).coerceIn(0.0, 1.0)
        val nearest = eye.distanceTo(a.lerp(b, fraction))
        val distance = eye.distanceTo(a.lerp(b, 0.5))
        return (
            ((nearest - NEAR) / (CLEAR - NEAR)).coerceIn(0.0, 1.0) *
                ((FAR - distance) / FADE).coerceIn(0.0, 1.0)
        ).toFloat()
    }

    private fun offset(seed: Long): Double = (seed and BYTE_MASK).toDouble() / BYTE_MASK * JITTER + CELL_INSET

    private companion object {
        const val BYTE_MASK = 255L
        const val JITTER = 0.6
        const val CELL_INSET = 0.2
        private const val CELL = 8.0
        private const val REACH = 4
        private const val PERIOD = 10f
        private const val LIFETIME = 4f
        private const val REVEAL = 0.65f
        private const val NEAR = 3.0
        private const val CLEAR = 7.0
        private const val FAR = 32.0
        private const val FADE = 8.0
        private const val WIDTH = 0.035
        private const val COLOR = 0xE6D7CF
        private const val ACCENT_EVERY = 7
        private const val ACCENT_LENGTH = 9.0
        private const val LENGTH = 5.0
        private const val SUBDUED_ALPHA = 0.35f
        private const val PHASE_STEP = 0.61803398875
        private const val TURN = 6.28318530718
    }
}
