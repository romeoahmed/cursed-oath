package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.combat.Fighter
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.combat.SorcererProfile
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
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
    fun purpleCarvesBeforeDamagingAndHitsEachTargetOnce(helper: GameTestHelper) {
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
                helper.assertTrue(target.health < HEALTH, "Purple must reach a target after excavating the path")
                helper.assertBlockPresent(Blocks.AIR, wall)
            }.thenExecuteAfter(SETTLE_TICKS) {
                helper.assertTrue(
                    target.health == HEALTH - TechniqueTuning.PURPLE_DAMAGE,
                    "An overlapping target must not be damaged twice",
                )
                wave.discard()
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun bedrockStopsPurpleAndProtectsTheFarSide(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val wall = BlockPos(14, 11, 18)
        helper.setBlock(wall, Blocks.BEDROCK)
        val target = helper.stationaryTarget(EntityTypes.HUSK, TARGET)
        val before = target.health
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(wave.isRemoved, "The protected surface must terminate Purple")
            helper.assertBlockPresent(Blocks.BEDROCK, wall)
            helper.assertTrue(target.health == before, "Targets behind the protected surface must be safe")
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun containersStopDestruction(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val chest = BlockPos(14, 11, 18)
        helper.setBlock(chest, Blocks.CHEST)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(wave.isRemoved, "A container must stop excavation")
            helper.assertBlockPresent(Blocks.CHEST, chest)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun fabricProtectionStopsTheCut(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val wall = BlockPos(14, 11, 18)
        val absolute = helper.absolutePos(wall)
        val owner = player.uuid
        PlayerBlockBreakEvents.BEFORE.register { _, actor, pos, _, _ -> actor.uuid != owner || pos != absolute }
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
    fun peripheralBedrockShieldsItsPathWithoutCancellingPurple(helper: GameTestHelper) {
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
            helper.assertBlockPresent(Blocks.STONE, behind)
            wave.discard()
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purpleDamagesTheNearSideBeforeStoppingAtBedrock(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val near = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(14, 10, 15))
        val wall = BlockPos(14, 11, 18)
        helper.setBlock(wall, Blocks.BEDROCK)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.succeedWhen {
            helper.assertTrue(wave.isRemoved, "A central protected surface must stop travel")
            helper.assertTrue(near.health < near.maxHealth, "A later obstacle must not erase an earlier contact")
        }
    }

    @GameTest(environment = "cursed-oath-test:waves")
    fun purpleRequiresReversalAndCancellationReleasesItsReservation(helper: GameTestHelper) {
        val player = helper.caster()
        val fighter = Fighter(player)
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
        val work = checkNotNull(TerrainDestruction.reserve(player))
        work.sphere(Vec3.atCenterOf(absolute), 1.0)
        helper.setBlock(pos, Blocks.CHEST)
        helper.succeedWhen {
            helper.assertTrue(work.finished, "The queued operation must settle")
            helper.assertTrue(work.blocked, "A newly placed container must be recorded as protected")
            helper.assertBlockPresent(Blocks.CHEST, pos)
        }
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun purpleRespectsTotemAndDoesNotSpendTheSameContactTwice(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        target.setItemSlot(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.defaultInstance)
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(target.offhandItem.isEmpty, "Lethal Purple must invoke native totem protection")
            }.thenExecuteAfter(SETTLE_TICKS) {
                helper.assertTrue(target.isAlive, "The same Purple must not kill the revived target again")
                wave.discard()
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun stalledPurpleCannotDamageAheadOfItsVisibleBody(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val stone = BlockPos.betweenClosed(BlockPos(8, 6, 6), BlockPos(13, 16, 16))
        stone.forEach { helper.setBlock(it, Blocks.STONE) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(14, 10, 17))
        val wave = helper.launchWave(player, Technique.PURPLE)
        helper.onEachTick {
            if (wave.z == player.z) {
                helper.assertTrue(target.health == target.maxHealth, "Queued travel must not project damage ahead")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(wave.z > player.z, "The body must advance after excavation")
            helper.assertTrue(target.health < target.maxHealth, "The advancing body must eventually make contact")
            wave.discard()
        }
    }

    private companion object {
        val ORIGIN = Vec3(14.5, 10.0, 9.5)
        val TARGET = BlockPos(14, 10, 21)
        const val SETTLE_TICKS = 20
        const val HEALTH = 500f
    }
}
