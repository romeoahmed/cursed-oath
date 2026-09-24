package io.github.romeoahmed.cursedoath.combat

import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.InfinityDefense
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Transient combat state for one player entity in one server level. */
internal class Fighter(
    val player: ServerPlayer,
) {
    data class Cast(
        val id: UUID,
        val technique: Technique,
        var remaining: Int,
        val terrain: TerrainDestruction.Work?,
    )

    private val saved = player.getAttachedOrCreate(SorcererData.RESOURCES)
    var energy = CursedEnergy(saved.energy)
        private set
    var recovery = saved.recovery
        private set
    var cast: Cast? = null
        private set
    var infinity = false
        private set
    private var pulseAt: Long? = null
    val pulseReady: Boolean
        get() = pulseAt?.let { player.level().gameTime - it in 0..PULSE_WINDOW } == true
    private val canPrepare: Boolean
        get() =
            player.isAlive && !player.isSpectator &&
                !player.isUsingItem && !player.isPassenger && !player.isFallFlying && !player.isSwimming

    fun prepare(technique: Technique) {
        if (technique == Technique.INFINITY && infinity) {
            infinity = false
        } else {
            val rejection = rejection(technique)
            when {
                rejection != null -> player.sendOverlayMessage(Component.translatable("message.cursed-oath.$rejection"))
                technique == Technique.INFINITY -> infinity = true
                else -> beginCast(technique)
            }
        }
    }

    private fun rejection(technique: Technique): String? {
        val reversal = player.getAttachedOrCreate(SorcererData.PROFILE).reversal
        return when {
            cast != null || recovery > 0 -> "busy"
            !canPrepare -> "hands"
            technique.requiresReversal && !reversal -> "qualification"
            technique == Technique.HEAL && player.health >= player.maxHealth -> "healthy"
            technique == Technique.INFINITY && energy.available < INFINITY_COST -> "energy"
            else -> null
        }
    }

    private fun beginCast(technique: Technique) {
        val prepared = energy.prepare(technique.cost.startup, technique.cost.release)
        if (prepared == null) {
            player.sendOverlayMessage(Component.translatable("message.cursed-oath.energy"))
        } else {
            val terrain = if (technique.destroysTerrain) TerrainDestruction.reserve(player) else null
            if (technique.destroysTerrain && terrain == null) {
                player.sendOverlayMessage(Component.translatable("message.cursed-oath.capacity"))
                return
            }
            energy = prepared
            val next = Cast(UUID.randomUUID(), technique, technique.preparation, terrain)
            cast = next
            pulseAt = null
            // Save the obligation before release; reconnecting cannot erase recovery.
            recovery = technique.preparation + technique.recovery
            persist()
            TechniqueCombat.event(player, next.id, technique, TechniqueEvent.PREPARE, player.eyePosition)
        }
    }

    fun preparePulse() {
        if (!canPrepare || cast != null || pulseReady) return
        energy = energy.spend(PULSE_COST) ?: return
        pulseAt = player.level().gameTime
        persist()
    }

    /** Consumed by the primary attack or an empty swing, never a sweep. */
    fun consumePulse(): Boolean {
        val ready = pulseReady && cast == null && player.isAlive && !player.isSpectator
        pulseAt = null
        return ready
    }

    fun tick() {
        if (!player.isAlive || player.isSpectator) {
            cancel()
            return
        }
        if (recovery > 0) recovery--
        if (pulseAt?.let { player.level().gameTime - it > PULSE_WINDOW } == true) pulseAt = null
        if (infinity) {
            val next = energy.spend(INFINITY_COST)
            infinity = next != null
            if (next != null) {
                energy = next
                InfinityDefense.interceptProjectiles(player)
            }
        }
        advanceCast()
        if (cast == null && recovery == 0 && !infinity) energy = energy.recover(RECOVERY_PER_TICK)
        persist()
    }

    private fun advanceCast() {
        val active = cast ?: return
        val qualified = !active.technique.requiresReversal || player.getAttachedOrCreate(SorcererData.PROFILE).reversal
        if (!canPrepare || !qualified || active.terrain?.finished == true) {
            cancel()
        } else if (--active.remaining <= 0) {
            energy = energy.release(active.technique.cost.release)
            cast = null
            persist()
            TechniqueCombat.release(player, active.id, active.technique, active.terrain)
        }
    }

    fun cancel() {
        cast?.let {
            it.terrain?.close()
            energy = energy.cancel(it.technique.cost.release)
            TechniqueCombat.event(player, it.id, it.technique, TechniqueEvent.CANCEL, player.eyePosition)
        }
        cast = null
        infinity = false
        pulseAt = null
        persist()
    }

    private fun persist() {
        val value = SorcererResources(energy.current, recovery)
        if (player.getAttached(SorcererData.RESOURCES) != value) player.setAttached(SorcererData.RESOURCES, value)
    }

    private companion object {
        const val PULSE_COST = 5
        const val PULSE_WINDOW = 60L
        const val INFINITY_COST = 2
        const val RECOVERY_PER_TICK = 2
    }
}
