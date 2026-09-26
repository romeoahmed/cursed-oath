package io.github.romeoahmed.cursedoath.network;

import static org.junit.jupiter.api.Assertions.*;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class CombatPayloadsTest {
    @Test
    void combatSnapshotRetainsEstablishedWireLayout() {
        var value = new CombatSnapshot(
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
                true);
        var buffer = Unpooled.buffer();
        try {
            CombatSnapshot.CODEC.encode(buffer, value);
            assertEquals("00112233445566778899aabbccddeeff0184077d2a020001c8016301", ByteBufUtil.hexDump(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecsPreserveIdentifiersPositionsAndBoundaryValues() {
        var session = UUID.randomUUID();
        roundTrip(CastRequest.CODEC, new CastRequest(session, Long.MAX_VALUE, CastRequest.PULSE));
        roundTrip(CombatSnapshot.CODEC, new CombatSnapshot(session, true, 900, 125, 42, 2, false, true, 200, 99, true));
        roundTrip(
                TechniqueEvent.CODEC,
                new TechniqueEvent(
                        CursedOath.id("test"),
                        session,
                        12,
                        4,
                        TechniqueEvent.BLACK_FLASH,
                        1L << 40,
                        new Vec3(29_999_999.25, -63.5, -29_999_999.75),
                        Vec3.ZERO));
    }

    private static <T> void roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        var buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            int boundary = buffer.writerIndex();
            codec.encode(buffer, value);
            assertEquals(value, codec.decode(buffer));
            assertEquals(boundary, buffer.readerIndex(), "Decoding must stop at the next payload boundary");
            assertEquals(value, codec.decode(buffer));
            assertFalse(buffer.isReadable(), "A decoder must consume exactly one encoded payload");
        } finally {
            buffer.release();
        }
    }
}
