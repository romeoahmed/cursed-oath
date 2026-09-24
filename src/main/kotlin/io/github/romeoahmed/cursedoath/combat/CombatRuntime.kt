package io.github.romeoahmed.cursedoath.combat

import io.github.romeoahmed.cursedoath.network.CastRequest
import io.github.romeoahmed.cursedoath.network.CombatSnapshot
import io.github.romeoahmed.cursedoath.network.RequestGate
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.InfinityDefense
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.IdentityHashMap
import java.util.UUID

object CombatRuntime {
    private const val SYNC_INTERVAL = 4L

    private val worlds = IdentityHashMap<ServerLevel, MutableMap<UUID, Fighter>>()
    private val connections = mutableMapOf<UUID, RequestGate>()

    fun initialize() {
        SorcererData.initialize()
        PayloadTypeRegistry.serverboundPlay().register(CastRequest.TYPE, CastRequest.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshot.TYPE, CombatSnapshot.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(TechniqueEvent.TYPE, TechniqueEvent.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(CastRequest.TYPE) { request, context ->
            request(context.player(), request)
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            connections[handler.player.uuid] = RequestGate()
            sync(fighter(handler.player))
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            remove(handler.player)
            connections.remove(handler.player.uuid)
        }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) remove(entity)
        }
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, amount, _ ->
            if (entity is ServerPlayer && amount > 0) {
                val fighter = worlds[entity.level()]?.get(entity.uuid)
                if (fighter?.cast != null) {
                    fighter.cancel()
                    sync(fighter)
                }
            }
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, player, _ -> sync(fighter(player)) }
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { player, _, _ -> sync(fighter(player)) }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            val player = entity as? ServerPlayer
            player == null || !InfinityDefense.blocks(player, source)
        }
        ServerTickEvents.END_LEVEL_TICK.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPED.register {
            worlds.clear()
            connections.clear()
            TechniqueCombat.clear()
        }
    }

    private fun fighter(player: ServerPlayer): Fighter {
        // A dimension transfer may retain the same player instance; retire its old world state first.
        for ((world, fighters) in worlds) {
            if (world !== player.level()) fighters.remove(player.uuid)?.cancel()
        }
        val fighters = worlds.getOrPut(player.level()) { mutableMapOf() }
        val previous = fighters[player.uuid]
        if (previous != null && previous.player === player) return previous
        previous?.cancel()
        return Fighter(player).also { fighters[player.uuid] = it }
    }

    fun practice(
        player: ServerPlayer,
        enabled: Boolean,
    ) {
        remove(player)
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = enabled, reversal = enabled))
        // Only the practice command resets resources; login and respawn retain the saved balance.
        if (enabled) player.setAttached(SorcererData.RESOURCES, SorcererResources())
        sync(fighter(player))
    }

    fun hasInfinity(player: ServerPlayer): Boolean =
        worlds[player.level()]?.get(player.uuid)?.infinity == true && player.isAlive && !player.isSpectator

    private fun request(
        player: ServerPlayer,
        request: CastRequest,
    ) {
        val gate = connections[player.uuid] ?: return
        if (!gate.accept(request.session, request.sequence, player.level().gameTime)) return
        val fighter = fighter(player)
        val profile = player.getAttachedOrCreate(SorcererData.PROFILE)
        if (!profile.practice || !player.isAlive || player.isSpectator) return
        when (request.technique) {
            CastRequest.CANCEL -> fighter.cancel()
            CastRequest.PULSE -> fighter.preparePulse()
            else -> Technique.fromWire(request.technique)?.let(fighter::prepare)
        }
        sync(fighter)
    }

    private fun tick(level: ServerLevel) {
        val fighters = worlds[level] ?: return
        // A released hit can kill another fighter and synchronously remove it through AFTER_DEATH.
        for (fighter in fighters.values.toList()) {
            val player = fighter.player
            if (fighters[player.uuid] !== fighter) continue
            if (!player.isAlive || player.isRemoved || player.level() !== level) {
                fighter.cancel()
                fighters.remove(player.uuid)
            } else {
                fighter.tick()
                if (level.gameTime % SYNC_INTERVAL == 0L) sync(fighter)
            }
        }
    }

    fun consumePulse(player: ServerPlayer): Boolean = worlds[player.level()]?.get(player.uuid)?.consumePulse() == true

    private fun remove(player: ServerPlayer) {
        for (fighters in worlds.values) fighters.remove(player.uuid)?.cancel()
    }

    private fun sync(fighter: Fighter) {
        val player = fighter.player
        val gate = connections[player.uuid] ?: return
        if (!ServerPlayNetworking.canSend(player, CombatSnapshot.TYPE)) return
        ServerPlayNetworking.send(
            player,
            CombatSnapshot(
                gate.session,
                player.getAttachedOrCreate(SorcererData.PROFILE).practice,
                fighter.energy.current,
                fighter.energy.reserved,
                fighter.recovery,
                fighter.cast?.technique?.wireId ?: 0,
                fighter.infinity,
                fighter.pulseReady,
            ),
        )
    }
}
