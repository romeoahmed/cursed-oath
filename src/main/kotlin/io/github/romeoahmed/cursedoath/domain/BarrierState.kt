package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.CursedOath
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.entity.Entity

/** Synced, unsaved defense state: 0 is inactive, positive values are Simple Domain strength, -1 is Amplification. */
object BarrierState {
    private val STATE =
        AttachmentRegistry.create<Int>(CursedOath.id("barrier_state")) {
            it.initializer { 0 }.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
        }

    fun initialize() = Unit

    fun get(entity: Entity): Int = entity.getAttached(STATE) ?: 0

    internal fun update(
        entity: Entity,
        value: Int,
    ) {
        if (get(entity) != value) entity.setAttached(STATE, value)
    }
}
