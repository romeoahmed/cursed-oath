package io.github.romeoahmed.cursedoath.network;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CombatSnapshot(
        UUID session,
        boolean enabled,
        int energy,
        int reserved,
        int recovery,
        int preparing,
        boolean infinity,
        boolean pulseReady,
        int burnout,
        int simpleDomain,
        boolean amplification)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CombatSnapshot> TYPE =
            new CustomPacketPayload.Type<>(CursedOath.id("state"));
    public static final StreamCodec<ByteBuf, CombatSnapshot> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            CombatSnapshot::session,
            ByteBufCodecs.BOOL,
            CombatSnapshot::enabled,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::energy,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::reserved,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::recovery,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::preparing,
            ByteBufCodecs.BOOL,
            CombatSnapshot::infinity,
            ByteBufCodecs.BOOL,
            CombatSnapshot::pulseReady,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::burnout,
            ByteBufCodecs.VAR_INT,
            CombatSnapshot::simpleDomain,
            ByteBufCodecs.BOOL,
            CombatSnapshot::amplification,
            CombatSnapshot::new);

    @Override
    public CustomPacketPayload.Type<CombatSnapshot> type() {
        return TYPE;
    }
}
