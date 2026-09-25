package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.beforeBlockBreak
import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.combat.SorcererProfile
import io.github.romeoahmed.cursedoath.fighter
import io.github.romeoahmed.cursedoath.launchWave
import io.github.romeoahmed.cursedoath.reserveTerrain
import io.github.romeoahmed.cursedoath.stationaryTarget
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class TechniqueWaveGameTest {
    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purplePiercesWallsAndHitsEachTargetOnce(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
        target.health = HEALTH
        val wall = BlockPos(14, 11, 18)
        helper.setBlock(wall, Blocks.STONE)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.assertTrue(target.health == HEALTH, "Release must not cause instant ray damage")
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(target.health < HEALTH, "Purple must reach the target independently of excavation")
            }.thenExecuteAfter(SETTLE_TICKS) {
                helper.assertTrue(
                    target.health == HEALTH - TechniqueTuning.PURPLE_DAMAGE,
                    "An overlapping target must not be damaged twice",
                )
                helper.assertBlockPresent(Blocks.AIR, wall)
                wave.discard()
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun bedrockIsPreservedWithoutShieldingTargets(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val wall = BlockPos(14, 11, 18)
        helper.setBlock(wall, Blocks.BEDROCK)
        val target = helper.stationaryTarget(EntityTypes.HUSK, TARGET)
        val before = target.health
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(wave.z > helper.absolutePos(wall).z, "Purple must pass the protected surface")
            helper.assertBlockPresent(Blocks.BEDROCK, wall)
            helper.assertTrue(target.health < before, "A preserved block must not shield entities")
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun containersRemainWithoutStoppingPurple(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val chest = BlockPos(14, 11, 18)
        helper.setBlock(chest, Blocks.CHEST)
        helper.setBlock(chest.south(), Blocks.WATER)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(wave.z > helper.absolutePos(chest).z, "Purple must pass a container")
            helper.assertBlockPresent(Blocks.CHEST, chest)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun fabricProtectionStopsTheCut(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val wall = BlockPos(14, 11, 18)
        val absolute = helper.absolutePos(wall)
        helper.beforeBlockBreak(player) { _, _, pos, _, _ -> pos != absolute }
        helper.setBlock(wall, Blocks.STONE)
        val wave = helper.launchWave(player, Technique.DISMANTLE)
        helper.succeedWhen {
            helper.assertTrue(wave.isRemoved, "A Fabric cancellation must stop the travelling cut")
            helper.assertBlockPresent(Blocks.STONE, wall)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun dismantleCutsAWideThinOpening(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val cut = BlockPos(18, 11, 18)
        val above = cut.above()
        helper.setBlock(cut, Blocks.STONE)
        helper.setBlock(above, Blocks.STONE)
        val target = helper.stationaryTarget(EntityTypes.HUSK, TARGET)
        val before = target.health
        val wave = helper.launchWave(player, Technique.DISMANTLE)
        helper.succeedWhen {
            helper.assertBlockPresent(Blocks.AIR, cut)
            helper.assertTrue(target.health < before, "The released cut must deal damage")
            helper.assertBlockPresent(Blocks.STONE, above)
            wave.discard()
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 120)
    fun peripheralBedrockDoesNotShieldTerrain(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val protected = BlockPos(18, 11, 18)
        val behind = protected.south(2)
        helper.setBlock(protected, Blocks.BEDROCK)
        helper.setBlock(behind, Blocks.STONE)
        val exposed = helper.stationaryTarget(EntityTypes.HUSK, TARGET)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(exposed.health < exposed.maxHealth, "Clear paths must still receive Purple damage")
            helper.assertBlockPresent(Blocks.BEDROCK, protected)
            helper.assertBlockPresent(Blocks.AIR, behind)
            wave.discard()
        }
    }

    @GameTest(environment = "cursed-oath-test:waves")
    fun purpleRequiresReversalAndCancellationReleasesItsReservation(helper: GameTestHelper) {
        val player = helper.caster()
        val fighter = helper.fighter(player)
        fighter.prepare(Technique.PURPLE)
        helper.assertTrue(fighter.cast == null, "Purple must require reversal training")
        player.setAttached(SorcererData.PROFILE, SorcererProfile(practice = true, reversal = true))
        fighter.prepare(Technique.PURPLE)
        val work = checkNotNull(fighter.cast?.terrain)
        fighter.cancel()
        helper.assertTrue(work.finished, "Cancelling must free terrain capacity")
        helper.assertTrue(fighter.energy.reserved == 0, "Cancelling must release reserved energy")
        helper.assertTrue(
            fighter.energy.current == CursedEnergy.CAPACITY - Technique.PURPLE.cost.startup,
            "Startup remains spent",
        )
        helper.assertTrue(
            helper.level
                .getEntities(TechniqueProjectiles.WAVE) {
                    it.getOwner() === player
                }.isEmpty(),
            "Cancellation must not spawn a wave",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun queuedExcavationChecksTheCurrentBlock(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val pos = BlockPos(14, 11, 18)
        val absolute = helper.absolutePos(pos)
        val work = checkNotNull(helper.reserveTerrain(player))
        work.sphere(Vec3.atCenterOf(absolute), 1.0)
        helper.setBlock(pos, Blocks.CHEST)
        helper.succeedWhen {
            helper.assertTrue(work.finished, "The queued operation must settle")
            helper.assertBlockPresent(Blocks.CHEST, pos)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purpleRespectsTotemAndDoesNotSpendTheSameContactTwice(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        target.setItemSlot(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.defaultInstance)
        val wave = helper.launchWave(player, Technique.PURPLE)
        repeat(FLIGHT_TICKS) { wave.tick() }
        helper.assertTrue(target.offhandItem.isEmpty, "Lethal Purple must invoke native totem protection")
        helper.assertTrue(target.isAlive, "The totem must revive the target")
        // Re-enter this wave without vanilla damage immunity or interference from other test arenas.
        target.invulnerableTime = 0
        target.setPos(wave.position())
        wave.tick()
        helper.assertTrue(target.isAlive, "The same Purple must not kill the revived target again")
        wave.discard()
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purpleMaintainsSpeedThroughDenseTerrainAfterCasterRemoval(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        BlockPos.betweenClosed(DENSE_MIN, DENSE_MAX).forEach {
            helper.setBlock(it, Blocks.STONE)
        }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        val wave = helper.launchWave(player, Technique.PURPLE)
        val start = wave.position()
        player.discard()
        repeat(FLIGHT_TICKS) { wave.tick() }
        helper.assertTrue(
            wave.position().distanceTo(start) == TechniqueTuning.PURPLE_SPEED * FLIGHT_TICKS,
            "Terrain density and caster removal must not change flight speed",
        )
        helper.assertTrue(target.health < target.maxHealth, "Flight must still damage targets inside terrain")
        wave.discard()
        val center = BlockPos(14, 11, 18)
        helper.succeedWhen {
            helper.assertBlockPresent(Blocks.AIR, center)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purplePreservesDeniedBlocksButExcavatesBehindThem(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val denied = BlockPos(14, 11, 18)
        val behind = denied.south(2)
        val absolute = helper.absolutePos(denied)
        helper.beforeBlockBreak(player) { _, _, pos, _, _ -> pos != absolute }
        helper.setBlock(denied, Blocks.STONE)
        helper.setBlock(behind, Blocks.STONE)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertBlockPresent(Blocks.AIR, behind)
            helper.assertBlockPresent(Blocks.STONE, denied)
            wave.discard()
        }
    }

    private companion object {
        val DENSE_MIN = BlockPos(8, 6, 6)
        val DENSE_MAX = BlockPos(20, 16, 24)
        const val FLIGHT_TICKS = 3
        val ORIGIN = Vec3(14.5, 10.0, 9.5)
        val TARGET = BlockPos(14, 10, 21)
        const val SETTLE_TICKS = 20
        const val HEALTH = 500f
    }
}
