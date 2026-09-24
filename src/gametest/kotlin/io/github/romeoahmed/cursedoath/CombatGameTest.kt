package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.combat.Fighter
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.combat.SorcererProfile
import io.github.romeoahmed.cursedoath.technique.CleaveContact
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.technique.TechniqueWave
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

class CombatGameTest {
    @GameTest(environment = "cursed-oath-test:combat")
    fun unavailableFlightEndsWithoutLoadingChunksOrLeakingTerrain(helper: GameTestHelper) {
        val distant = Vec3(10_000.5, 10.0, 10_000.5)
        val player = helper.caster().apply { setPos(helper.absoluteVec(distant)) }
        val chunk = ChunkPos.containing(player.blockPosition())
        helper.assertTrue(helper.level.chunkSource.getChunkNow(chunk.x, chunk.z) == null, "Fixture must be unloaded")
        for (technique in listOf(Technique.BLUE, Technique.RED, Technique.PURPLE, Technique.DISMANTLE)) {
            val work = checkNotNull(TerrainDestruction.reserve(player))
            val projectile =
                if (technique == Technique.BLUE || technique == Technique.RED) {
                    TechniqueOrb(TechniqueProjectiles.ORB, helper.level).apply { configure(player, technique, work) }
                } else {
                    TechniqueWave(TechniqueProjectiles.WAVE, helper.level).apply { configure(player, technique, work) }
                }
            // These entities are deliberately unregistered: only this call advances them.
            projectile.tick()
            helper.assertTrue(projectile.isRemoved && work.finished, "$technique must release its terrain slot")
        }
        helper.assertTrue(helper.level.chunkSource.getChunkNow(chunk.x, chunk.z) == null, "Flight must not load chunks")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun cleaveDoesNotLoadChunksForItsContactRay(helper: GameTestHelper) {
        val distant = Vec3(10_000.5, 10.0, 10_000.5)
        val player = helper.caster().apply { setPos(helper.absoluteVec(distant)) }
        val chunk = ChunkPos.containing(player.blockPosition())
        helper.assertTrue(helper.level.chunkSource.getChunkNow(chunk.x, chunk.z) == null, "Fixture must be unloaded")
        val work = checkNotNull(TerrainDestruction.reserve(player))
        helper.assertTrue(
            CleaveContact.release(player, work) == null && work.finished,
            "Unavailable contact is rejected",
        )
        helper.assertTrue(
            helper.level.chunkSource.getChunkNow(chunk.x, chunk.z) == null,
            "Contact checks must not synchronously load a chunk",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat", structure = "cursed-oath-test:arena")
    fun cleaveMissHasNoImpactAndReleasesTerrainCapacity(helper: GameTestHelper) {
        val player = helper.caster()
        val work = checkNotNull(TerrainDestruction.reserve(player))
        helper.assertTrue(CleaveContact.release(player, work) == null, "A miss must not publish a successful impact")
        helper.assertTrue(work.finished, "A miss must free the terrain reservation")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat", structure = "cursed-oath-test:arena")
    fun cleaveUsesFourBlockSurfaceReach(helper: GameTestHelper) {
        val player = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(2, 1, 6))
        val beyond = 4.1
        val inside = 3.9
        val halfWidth = target.boundingBox.zsize / 2
        target.setPos(player.position().add(0.0, 0.0, beyond + halfWidth))
        val miss = checkNotNull(TerrainDestruction.reserve(player))
        helper.assertTrue(CleaveContact.release(player, miss) == null, "The real surface beyond reach must be rejected")
        target.setPos(player.position().add(0.0, 0.0, inside + halfWidth))
        val hit = checkNotNull(TerrainDestruction.reserve(player))
        try {
            helper.assertTrue(CleaveContact.release(player, hit) != null, "Contact near four blocks must be accepted")
            helper.assertTrue(target.health < target.maxHealth, "Accepted contact must cause damage")
        } finally {
            hit.close()
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 40)
    fun healingChecksQualificationAndCommitsOnlyAfterPreparation(helper: GameTestHelper) {
        val player = helper.caster()
        val fighter = Fighter(player)
        val injuredHealth = 10f
        val healing = 6f
        player.health = injuredHealth
        fighter.prepare(Technique.HEAL)
        helper.assertTrue(fighter.cast == null, "Self-healing requires reversal training")
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = true, reversal = true))
        fighter.prepare(Technique.HEAL)
        helper.assertTrue(player.health == injuredHealth, "Preparation must not heal early")
        helper.onEachTick { fighter.tick() }
        helper.succeedWhen {
            helper.assertTrue(fighter.cast == null, "Healing must finish preparation")
            helper.assertTrue(player.health == injuredHealth + healing, "Healing must use the native health boundary")
            val spent = Technique.HEAL.cost.startup + Technique.HEAL.cost.release
            helper.assertTrue(fighter.energy.current == CursedEnergy.CAPACITY - spent, "Release is paid exactly once")
            helper.assertTrue(fighter.energy.reserved == 0 && fighter.recovery > 0, "Recovery survives release")
        }
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun contactCannotReachThroughAThinBarrier(helper: GameTestHelper) {
        val player = helper.caster()
        // The vertical south-facing trapdoor occupies z=3..3.1875.
        val wall =
            Blocks.OAK_TRAPDOOR
                .defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH)
        val wallPos = BlockPos(2, 2, 3)
        val targetPos = Vec3(2.5, 1.0, 3.6)
        helper.setBlock(wallPos, wall)
        val target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, wallPos.below())
        target.setPos(helper.absoluteVec(targetPos))
        val reach = 3.0
        val hit = TechniqueCombat.contact(player, reach)
        helper.assertTrue(
            hit?.type == HitResult.Type.BLOCK,
            "Contact must stop at the thin barrier before reaching the target",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun cleaveHitsAnOverlappingTarget(helper: GameTestHelper) {
        val player = helper.caster()
        val target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(2, 1, 1))
        val before = target.health
        val work = checkNotNull(TerrainDestruction.reserve(player))
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work)
        work.close()
        helper.assertTrue(target.health < before, "A ray starting inside a target must still hit")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun switchingToSpectatorCancelsWithoutRefundingStartup(helper: GameTestHelper) {
        val player = helper.caster()
        val fighter = Fighter(player)
        fighter.prepare(Technique.BLUE)
        player.setGameMode(GameType.SPECTATOR)
        fighter.tick()
        helper.assertTrue(fighter.cast == null, "Spectators must not finish a prepared cast")
        helper.assertTrue(fighter.energy.reserved == 0, "Cancellation must release the reservation")
        helper.assertTrue(
            fighter.energy.current ==
                CursedEnergy.CAPACITY - Technique.BLUE.cost.startup,
            "Only startup energy should remain spent",
        )
        helper.assertTrue(fighter.recovery > 0, "Cancellation must retain recovery")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun cancellationAndReconnectPreserveResourceObligations(helper: GameTestHelper) {
        val player = helper.caster()
        val fighter = Fighter(player)
        fighter.prepare(Technique.BLUE)
        val remaining = fighter.recovery
        fighter.cancel()
        val reconnected = Fighter(player)
        helper.assertTrue(reconnected.energy == fighter.energy, "Reconnection must not refill energy")
        helper.assertTrue(reconnected.recovery == remaining, "Reconnection must retain recovery")
        helper.assertTrue(reconnected.cast == null, "Reservations must not be restored as active casts")
        helper.succeed()
    }
}
