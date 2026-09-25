package io.github.romeoahmed.cursedoath.world

import io.github.romeoahmed.cursedoath.beforeBlockBreak
import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.combat.SorcererProfile
import io.github.romeoahmed.cursedoath.fighter
import io.github.romeoahmed.cursedoath.reserveTerrain
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class TerrainDestructionGameTest {
    @GameTest(environment = "cursed-oath-test:terrain")
    fun callbackCancellationPreventsBlockRemoval(helper: GameTestHelper) {
        val player = helper.caster()
        val position = BlockPos(2, 1, 2)
        val absolute = helper.absolutePos(position)
        helper.setBlock(position, Blocks.STONE)
        val work = checkNotNull(helper.reserveTerrain(player))
        work.sphere(Vec3.atCenterOf(absolute), 1.0)
        var completed = false
        var canceled = false
        work.onComplete = { completed = true }
        helper.beforeBlockBreak(player) { _, _, pos, _, _ ->
            if (pos == absolute) {
                canceled = true
                work.close()
            }
            true
        }
        helper.succeedWhen {
            helper.assertTrue(canceled && work.finished, "The callback must close the active task")
            helper.assertTrue(!completed, "Canceled work must not run its completion callback")
            helper.assertBlockPresent(Blocks.STONE, position)
        }
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    fun removedOwnerCannotCommitQueuedWork(helper: GameTestHelper) {
        val player = helper.caster()
        val pos = BlockPos(2, 1, 2)
        helper.setBlock(pos, Blocks.STONE)
        val work = checkNotNull(helper.reserveTerrain(player))
        work.sphere(Vec3.atCenterOf(helper.absolutePos(pos)), 1.0)
        var completed = false
        work.onComplete = { completed = true }
        player.discard()
        work.advance()
        helper.assertTrue(work.finished && !completed, "Removal must cancel work even if health remains positive")
        helper.assertBlockPresent(Blocks.STONE, pos)
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    fun capacityRejectionDoesNotSpendEnergy(helper: GameTestHelper) {
        val player = helper.caster()
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = true, reversal = true))
        val reservations = mutableListOf<TerrainDestruction.Work>()
        try {
            while (true) reservations.add(helper.reserveTerrain(player) ?: break)
            val fighter = helper.fighter(player)
            fighter.prepare(Technique.PURPLE)
            helper.assertTrue(fighter.cast == null, "An over-capacity cast must be rejected")
            helper.assertTrue(
                fighter.energy.current == CursedEnergy.CAPACITY,
                "Rejected work must not spend startup energy",
            )
            helper.assertTrue(fighter.energy.reserved == 0, "Rejected work must not reserve release energy")
        } finally {
            reservations.forEach(TerrainDestruction.Work::close)
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    fun removingQualificationDuringPreparationCancelsPurple(helper: GameTestHelper) {
        val player = helper.caster()
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = true, reversal = true))
        val fighter = helper.fighter(player)
        fighter.prepare(Technique.PURPLE)
        val work = checkNotNull(fighter.cast?.terrain)
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = true, reversal = false))
        fighter.tick()
        helper.assertTrue(fighter.cast == null && work.finished, "Release must recheck qualification and free its slot")
        helper.assertTrue(fighter.energy.reserved == 0, "Revoked qualification must cancel the release reservation")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun removalBudgetAndQueuedCancellationAreBounded(helper: GameTestHelper) {
        val player = helper.caster()
        val blocks = BlockPos.betweenClosed(BlockPos(3, 2, 4), BlockPos(15, 14, 16)).map { it.immutable() }
        blocks.forEach { helper.setBlock(it, Blocks.DIRT) }
        val work = checkNotNull(helper.reserveTerrain(player))
        val center = helper.absoluteVec(Vec3(9.0, 8.0, 10.0))
        val radius = 9.0
        work.sphere(center, radius)
        TerrainDestruction.tick()
        val removed = blocks.count { helper.getBlockState(it).isAir }
        val writeBudget = TerrainDestruction.WRITES_PER_TICK
        helper.assertTrue(removed in 0..writeBudget, "A tick must stay within the direct block-removal budget")
        helper.assertTrue(!work.finished, "A large operation must wait across ticks")
        work.close()
        helper.runAfterDelay(2) {
            helper.assertTrue(
                blocks.count { helper.getBlockState(it).isAir } == removed,
                "Closed work must not keep excavating",
            )
            helper.succeed()
        }
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun protectedBlockDoesNotCancelTheRestOfASphere(helper: GameTestHelper) {
        val player = helper.caster()
        val center = BlockPos(8, 6, 10)
        val stone = center.east(2)
        helper.setBlock(center, Blocks.CHEST)
        helper.setBlock(stone, Blocks.STONE)
        val work = checkNotNull(helper.reserveTerrain(player))
        val radius = 4.0
        work.sphere(Vec3.atCenterOf(helper.absolutePos(center)), radius)
        helper.succeedWhen {
            helper.assertTrue(work.finished, "Excavation must settle")
            helper.assertBlockPresent(Blocks.CHEST, center)
            helper.assertBlockPresent(Blocks.AIR, stone)
        }
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun callbackReplacementIsRecheckedAndShieldsTheRestOfItsColumn(helper: GameTestHelper) {
        val player = helper.caster()
        val position = BlockPos(8, 5, 6)
        val behind = position.south()
        val absolute = helper.absolutePos(position)
        helper.setBlock(position, Blocks.STONE)
        helper.setBlock(behind, Blocks.STONE)
        helper.beforeBlockBreak(player) { level, _, pos, _, _ ->
            if (pos == absolute) level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState())
            true
        }
        val work = checkNotNull(helper.reserveTerrain(player))
        val start = helper.absoluteVec(Vec3(8.5, 5.5, 4.5))
        val length = 4.0
        val thickness = 0.1
        work.cuts(listOf(SweptVolume(start, start.add(0.0, 0.0, length), Vec3(thickness, thickness, thickness))))
        var completions = 0
        work.onComplete = { completions++ }
        helper.succeedWhen {
            helper.assertTrue(work.finished, "The sweep must complete")
            helper.assertBlockPresent(Blocks.CHEST, position)
            helper.assertBlockPresent(Blocks.STONE, behind)
            helper.assertTrue(completions == 1, "Completion must run exactly once")
        }
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun invalidOwnerCancelsTerrainAndDeferredDamage(helper: GameTestHelper) {
        val player = helper.caster()
        val position = BlockPos(8, 5, 6)
        helper.setBlock(position, Blocks.STONE)
        val work = checkNotNull(helper.reserveTerrain(player))
        work.sphere(Vec3.atCenterOf(helper.absolutePos(position)), 1.0)
        var completed = false
        work.onComplete = { completed = true }
        player.setGameMode(GameType.SPECTATOR)
        helper.succeedWhen {
            helper.assertTrue(work.finished && !completed, "Invalid work must not commit deferred damage")
            helper.assertBlockPresent(Blocks.STONE, position)
        }
    }
}
