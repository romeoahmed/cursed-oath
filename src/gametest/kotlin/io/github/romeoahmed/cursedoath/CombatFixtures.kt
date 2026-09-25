package io.github.romeoahmed.cursedoath

import com.mojang.authlib.GameProfile
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.combat.Fighter
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.technique.TechniqueWave
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.util.UUID

internal fun <T : Mob> GameTestHelper.stationaryTarget(
    type: EntityType<T>,
    pos: BlockPos,
): T =
    spawnWithNoFreeWill(type, pos).apply {
        setNoAi(true)
        setNoGravity(true)
        if (this is AgeableMob) age = 0
        onFinish { discard() }
    }

internal fun GameTestHelper.caster(): ServerPlayer {
    val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), "combat-test"), false)
    val player = ServerPlayer(level.server, level, cookie.gameProfile(), cookie.clientInformation())
    player.connection =
        ServerGamePacketListenerImpl(level.server, Connection(PacketFlow.SERVERBOUND), player, cookie)
    player.setGameMode(GameType.SURVIVAL)
    val origin = absoluteVec(Vec3(2.5, 1.0, 1.5))
    player.setPos(origin)
    player.yRot = 0f
    player.xRot = 0f
    onFinish {
        Domains.ownedBy(player)?.let(Domains::end)
        CombatRuntime.fighters(player.level()).firstOrNull { it.player === player }?.cancel()
        player.discard()
    }
    return player
}

internal fun GameTestHelper.launchWave(
    player: ServerPlayer,
    technique: Technique,
): TechniqueWave {
    release(player, technique)
    return level.getEntities(TechniqueProjectiles.WAVE) { it.getOwner() === player }.single()
}

internal fun GameTestHelper.reserveTerrain(player: ServerPlayer): TerrainDestruction.Work? =
    TerrainDestruction.reserve(player)?.also { work -> onFinish { work.close() } }

internal fun GameTestHelper.release(
    player: ServerPlayer,
    technique: Technique,
) {
    val work = if (technique.destroysTerrain) checkNotNull(reserveTerrain(player)) else null
    TechniqueCombat.release(player, UUID.randomUUID(), technique, work)
    val projectiles =
        level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player } +
            level.getEntities(TechniqueProjectiles.WAVE) { it.getOwner() === player }
    onFinish { projectiles.forEach { it.discard() } }
}

internal fun GameTestHelper.fighter(player: ServerPlayer): Fighter =
    Fighter(player).also { fighter -> onFinish { fighter.cancel() } }
