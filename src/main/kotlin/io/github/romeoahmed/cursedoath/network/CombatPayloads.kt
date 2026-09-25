package io.github.romeoahmed.cursedoath.network

import io.github.romeoahmed.cursedoath.CursedOath
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import java.util.UUID

data class CastRequest(
    val session: UUID,
    val sequence: Long,
    val technique: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val CANCEL = 0
        const val PULSE = -1
        val TYPE = CustomPacketPayload.Type<CastRequest>(CursedOath.id("cast"))
        val CODEC: StreamCodec<ByteBuf, CastRequest> =
            StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                CastRequest::session,
                ByteBufCodecs.LONG,
                CastRequest::sequence,
                ByteBufCodecs.VAR_INT,
                CastRequest::technique,
                ::CastRequest,
            )
    }
}

data class CombatSnapshot(
    val session: UUID,
    val enabled: Boolean,
    val energy: Int,
    val reserved: Int,
    val recovery: Int,
    val preparing: Int,
    val infinity: Boolean,
    val pulseReady: Boolean,
    val burnout: Int = 0,
    val simpleDomain: Int = 0,
    val amplification: Boolean = false,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CombatSnapshot>(CursedOath.id("state"))
        val CODEC: StreamCodec<ByteBuf, CombatSnapshot> =
            StreamCodec.composite(
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
                ::CombatSnapshot,
            )
    }
}

data class TechniqueEvent(
    val dimension: Identifier,
    val id: UUID,
    val actor: Int,
    val technique: Int,
    val stage: Int,
    val tick: Long,
    val origin: Vec3,
    val destination: Vec3,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val PREPARE = 0
        const val RELEASE = 1
        const val CANCEL = 2
        const val BLACK_FLASH = 3
        const val IMPACT = 4
        val TYPE = CustomPacketPayload.Type<TechniqueEvent>(CursedOath.id("effect"))
        val CODEC: StreamCodec<ByteBuf, TechniqueEvent> =
            StreamCodec.composite(
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
                ::TechniqueEvent,
            )
    }
}
