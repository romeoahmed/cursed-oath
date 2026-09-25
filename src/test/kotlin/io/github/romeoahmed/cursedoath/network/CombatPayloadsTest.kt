package io.github.romeoahmed.cursedoath.network

import io.github.romeoahmed.cursedoath.CursedOath
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CombatPayloadsTest {
    @Test
    fun `combat snapshot retains the established wire layout`() {
        val value =
            CombatSnapshot(
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                true,
                900,
                125,
                42,
                2,
                false,
                true,
                200,
                99,
                true,
            )
        val buffer = Unpooled.buffer()
        try {
            CombatSnapshot.CODEC.encode(buffer, value)
            assertEquals("00112233445566778899aabbccddeeff0184077d2a020001c8016301", ByteBufUtil.hexDump(buffer))
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `payload codecs preserve identifiers positions and boundary values`() {
        val session = UUID.randomUUID()
        roundTrip(CastRequest.CODEC, CastRequest(session, Long.MAX_VALUE, CastRequest.PULSE))
        roundTrip(CombatSnapshot.CODEC, CombatSnapshot(session, true, 900, 125, 42, 2, false, true, 200, 99, true))
        roundTrip(
            TechniqueEvent.CODEC,
            TechniqueEvent(
                CursedOath.id("test"),
                session,
                12,
                4,
                TechniqueEvent.BLACK_FLASH,
                1L shl 40,
                Vec3(29_999_999.25, -63.5, -29_999_999.75),
                Vec3.ZERO,
            ),
        )
    }

    private fun <T : Any> roundTrip(
        codec: StreamCodec<ByteBuf, T>,
        value: T,
    ) {
        val buffer = Unpooled.buffer()
        try {
            codec.encode(buffer, value)
            assertEquals(value, codec.decode(buffer))
            assertFalse(buffer.isReadable, "A decoder must consume exactly one encoded payload")
        } finally {
            buffer.release()
        }
    }
}
