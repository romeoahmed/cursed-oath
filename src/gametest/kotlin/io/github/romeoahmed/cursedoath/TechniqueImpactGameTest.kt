package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.technique.RedBlast
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

class TechniqueImpactGameTest {
    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun blueDeliversItsCompleteCompressionOutput(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        helper.level.addNewPlayer(player)
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
        target.health = HEALTH
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.BLUE)
        val orb = helper.level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player }.single()
        orb.setPos(target.boundingBox.center)
        orb.deltaMovement = Vec3.ZERO
        helper.runAfterDelay(TechniqueTuning.BLUE_DURATION.toLong() + 1) {
            player.discard()
            helper.assertTrue(orb.isRemoved, "The visible core must expire with its attraction")
            helper.assertTrue(
                target.health == HEALTH - TechniqueTuning.BLUE_OUTPUT,
                "Blue must deliver six core pulses",
            )
            helper.succeed()
        }
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena")
    fun redPeakDoublesCompleteBlueOutput(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
        target.health = HEALTH
        RedBlast.impact(player, target.boundingBox.center, player.lookAngle)
        helper.assertTrue(
            target.health == HEALTH - TechniqueTuning.BLUE_OUTPUT * 2,
            "Uncovered Red must double Blue output; health=${target.health}",
        )
        val minimumRepulsion = 3.0
        helper.assertTrue(
            target.deltaMovement.z > minimumRepulsion,
            "Red must deliver its stronger directional impulse",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun cleaveExcavatesIntersectingCutsAtContact(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val work = checkNotNull(TerrainDestruction.reserve(player))
        val wall = BlockPos.betweenClosed(BlockPos(9, 7, 11), BlockPos(19, 17, 14)).map { it.immutable() }
        wall.forEach { helper.setBlock(it, Blocks.STONE) }
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work)
        helper.succeedWhen {
            helper.assertTrue(work.finished, "Cleave terrain work must settle")
            val contact = BlockPos(14, 11, 11)
            helper.assertBlockPresent(Blocks.AIR, contact)
            val removed = wall.count { helper.getBlockState(it).isAir }
            helper.assertTrue(removed > MIN_CLEAVE_BLOCKS, "Contact cutting must open a substantial lattice")
            helper.assertTrue(removed < wall.size, "Cleave must preserve material between the cutting planes")
        }
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun cleaveAdaptsToDurabilityWithoutRepeatingTheInitialHit(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(14, 10, 11))
        checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
        target.health = HEALTH
        val work = checkNotNull(TerrainDestruction.reserve(player))
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work)
        helper.assertTrue(
            target.health == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE,
            "Cleave must adapt up to its output cap",
        )
        helper.assertTrue(
            HEALTH - target.health > TechniqueTuning.DISMANTLE_DAMAGE,
            "Contact Cleave must exceed Dismantle",
        )
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(work.finished, "The contact lattice must finish")
            }.thenExecuteAfter(SETTLE_TICKS) {
                helper.assertTrue(
                    target.health == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE,
                    "The initial target is hit once",
                )
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun cleaveCutsThroughStoneAndDamagesMultipleTargetsOnce(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val wall = BlockPos.betweenClosed(BlockPos(8, 5, 11), BlockPos(21, 18, 12))
        wall.forEach { helper.setBlock(it, Blocks.STONE) }
        val targets =
            listOf(BlockPos(12, 10, 15), BlockPos(16, 10, 15)).map { pos ->
                helper.stationaryTarget(EntityTypes.VILLAGER, pos).apply {
                    checkNotNull(getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
                    health = HEALTH
                }
            }
        val work = checkNotNull(TerrainDestruction.reserve(player))
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work)
        helper.assertTrue(targets.all { it.health == HEALTH }, "Damage must wait for excavation")
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(work.finished, "The lattice must finish its terrain work")
            }.thenExecuteAfter(SETTLE_TICKS) {
                helper.assertTrue(
                    targets.all { it.health == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE },
                    "Every exposed target must receive one adaptive hit, including behind the former wall",
                )
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun cleavePreservesProtectedColumnsAndGridGaps(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val contact = BlockPos(14, 11, 11)
        helper.setBlock(contact, Blocks.STONE)
        val shield = BlockPos(16, 11, 13)
        helper.setBlock(shield, Blocks.BEDROCK)
        val shielded = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(16, 10, 15))
        val gap = helper.stationaryTarget(EntityTypes.RABBIT, BlockPos(15, 12, 15))
        val work = checkNotNull(TerrainDestruction.reserve(player))
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work)
        helper.succeedWhen {
            helper.assertTrue(work.finished, "The lattice must settle")
            helper.assertBlockPresent(Blocks.BEDROCK, shield)
            helper.assertTrue(shielded.health == shielded.maxHealth, "A protected column must shield its far side")
            helper.assertTrue(gap.health == gap.maxHealth, "A target between cutting planes must remain untouched")
        }
    }

    private companion object {
        val ORIGIN = Vec3(14.5, 10.0, 9.5)
        val TARGET = BlockPos(14, 10, 14)
        const val SETTLE_TICKS = 20
        const val HEALTH = 500f
        const val MIN_CLEAVE_BLOCKS = 100
    }
}
