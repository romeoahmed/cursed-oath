package io.github.romeoahmed.cursedoath.network;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CastRequest(UUID session, long sequence, int technique) implements CustomPacketPayload {
    public static final int CANCEL = 0;
    public static final int PULSE = -1;
    public static final CustomPacketPayload.Type<CastRequest> TYPE =
            new CustomPacketPayload.Type<>(CursedOath.id("cast"));
    public static final StreamCodec<ByteBuf, CastRequest> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            CastRequest::session,
            ByteBufCodecs.LONG,
            CastRequest::sequence,
            ByteBufCodecs.VAR_INT,
            CastRequest::technique,
            CastRequest::new);

    @Override
    public CustomPacketPayload.Type<CastRequest> type() {
        return TYPE;
    }
}
