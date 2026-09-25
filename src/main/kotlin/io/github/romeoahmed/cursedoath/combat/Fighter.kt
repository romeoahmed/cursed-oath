package io.github.romeoahmed.cursedoath.combat

import io.github.romeoahmed.cursedoath.domain.BarrierState
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
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
    val defense = Defense(player)
    val infinity: Boolean get() = defense.infinity
    var burnout = saved.burnout
        private set
    private var pulseAt: Long? = null
    val pulseReady: Boolean
        get() = pulseAt?.let { player.level().gameTime - it in 0..PULSE_WINDOW } == true

    fun prepare(technique: Technique) {
        if (technique.domain && Domains.ownedBy(player) != null) {
            Domains.cancel(player)
            return
        }
        if (technique == Technique.SIMPLE_DOMAIN || technique == Technique.AMPLIFICATION) {
            prepareDefense(technique)
            return
        }
        if (technique == Technique.INFINITY && infinity) {
            defense.infinity = false
        } else {
            val rejection = CastRules.rejection(this, technique)
            when {
                rejection != null -> player.sendOverlayMessage(Component.translatable("message.cursed-oath.$rejection"))
                technique == Technique.INFINITY -> defense.infinity = true
                else -> beginCast(technique)
            }
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

    private fun prepareDefense(technique: Technique) {
        if (!player.isAlive || player.isSpectator || Domains.isOverloaded(player)) return
        if (!player.getAttachedOrCreate(SorcererData.PROFILE).barriers) return
        energy = defense.toggle(technique, energy)
        if (defense.amplification) cancelPreparation()
        persist()
    }

    fun imposeBurnout(ticks: Int) {
        burnout = maxOf(burnout, ticks)
        if (Domains.ownedBy(player) == null) defense.infinity = false
        persist()
    }

    fun preparePulse() {
        if (!CastRules.available(player, Technique.CLEAVE) || cast != null || pulseReady) return
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
        if (burnout > 0 && Domains.ownedBy(player) == null) burnout--
        if (pulseAt?.let { player.level().gameTime - it > PULSE_WINDOW } == true) pulseAt = null
        energy = defense.tick(energy)
        advanceCast()
        val resting = cast == null && recovery == 0 && !defense.sustained
        if (resting && Domains.ownedBy(player) == null) energy = energy.recover(RECOVERY_PER_TICK)
        persist()
    }

    private fun advanceCast() {
        val active = cast ?: return
        val available = CastRules.available(player, active.technique) && CastRules.qualified(player, active.technique)
        if (!available || active.terrain?.finished == true) {
            cancel()
        } else if (--active.remaining <= 0) {
            energy = energy.release(active.technique.cost.release)
            cast = null
            persist()
            TechniqueCombat.release(player, active.id, active.technique, active.terrain)
        }
    }

    private fun cancelPreparation() {
        cast?.let {
            it.terrain?.close()
            energy = energy.cancel(it.technique.cost.release)
            TechniqueCombat.event(player, it.id, it.technique, TechniqueEvent.CANCEL, player.eyePosition)
        }
        cast = null
        pulseAt = null
        persist()
    }

    fun cancel() {
        cancelPreparation()
        defense.clear()
        Domains.cancel(player)
        persist()
    }

    private fun persist() {
        BarrierState.update(player, if (defense.amplification) -1 else defense.simple)
        val value = SorcererResources(energy.current, recovery, burnout)
        if (player.getAttached(SorcererData.RESOURCES) != value) player.setAttached(SorcererData.RESOURCES, value)
    }

    private companion object {
        const val PULSE_COST = 5
        const val PULSE_WINDOW = 60L
        const val RECOVERY_PER_TICK = 2
    }
}
