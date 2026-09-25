package io.github.romeoahmed.cursedoath.network;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record TechniqueEvent(
        Identifier dimension, UUID id, int actor, int technique, int stage, long tick, Vec3 origin, Vec3 destination)
        implements CustomPacketPayload {
    public static final int PREPARE = 0;
    public static final int RELEASE = 1;
    public static final int CANCEL = 2;
    public static final int BLACK_FLASH = 3;
    public static final int IMPACT = 4;
    public static final CustomPacketPayload.Type<TechniqueEvent> TYPE =
            new CustomPacketPayload.Type<>(CursedOath.id("effect"));
    public static final StreamCodec<ByteBuf, TechniqueEvent> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            TechniqueEvent::dimension,
            UUIDUtil.STREAM_CODEC,
            TechniqueEvent::id,
            ByteBufCodecs.VAR_INT,
            TechniqueEvent::actor,
            ByteBufCodecs.VAR_INT,
            TechniqueEvent::technique,
            ByteBufCodecs.VAR_INT,
            TechniqueEvent::stage,
            ByteBufCodecs.LONG,
            TechniqueEvent::tick,
            Vec3.STREAM_CODEC,
            TechniqueEvent::origin,
            Vec3.STREAM_CODEC,
            TechniqueEvent::destination,
            TechniqueEvent::new);

    @Override
    public CustomPacketPayload.Type<TechniqueEvent> type() {
        return TYPE;
    }
}
