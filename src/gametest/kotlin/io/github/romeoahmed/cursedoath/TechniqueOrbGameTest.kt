package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

class TechniqueOrbGameTest {
    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun redTravelsBeforeDamageAndDetonatesOnce(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
        target.health = HEALTH
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.RED)
        val orb = helper.level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player }.single()
        helper.assertTrue(target.health == HEALTH, "Release must not deal remote damage")
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(orb.z > player.z && !orb.isRemoved, "Red must occupy an intermediate flight position")
                helper.assertTrue(target.health == HEALTH, "Remote targets stay unharmed during initial flight")
            }.thenWaitUntil {
                helper.assertTrue(orb.isRemoved, "Red must be removed after detonation")
                helper.assertTrue(target.health < HEALTH, "The core must reach and damage the target")
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun wallBuiltAfterReleaseInterceptsRed(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, TARGET)
        val before = target.health
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.RED)
        val orb = helper.level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player }.single()
        val wall = BlockPos.betweenClosed(BlockPos(11, 8, 13), BlockPos(17, 15, 13))
        wall.forEach { helper.setBlock(it, Blocks.BEDROCK) }
        helper.succeedWhen {
            helper.assertTrue(orb.isRemoved, "The new wall must intercept the travelling core")
            helper.assertTrue(target.health == before, "The intercepted attack must not damage the far side")
            val protected = BlockPos(14, 11, 13)
            helper.assertBlockPresent(Blocks.BEDROCK, protected)
        }
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun blueFliesThenSettlesAtTheNewObstacle(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        helper.level.addNewPlayer(player)
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.BLUE)
        val orb = helper.level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player }.single()
        val wall = BlockPos(14, 11, 14)
        helper.setBlock(wall, Blocks.STONE)
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(orb.z > player.z && orb.deltaMovement != Vec3.ZERO, "Blue must launch and move")
            }.thenWaitUntil {
                helper.assertTrue(orb.deltaMovement == Vec3.ZERO, "Blue must settle at contact")
                helper.assertTrue(!orb.isRemoved, "Settled Blue must remain as the visible compression field")
                helper.assertBlockPresent(Blocks.AIR, wall)
                orb.discard()
                player.discard()
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    fun settledBlueEndsWhenItsCasterIsReplaced(helper: GameTestHelper) {
        val player = helper.caster().apply { setPos(helper.absoluteVec(ORIGIN)) }
        helper.level.addNewPlayer(player)
        val wall = BlockPos(14, 11, 14)
        helper.setBlock(wall, Blocks.BEDROCK)
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.BLUE)
        val orb = helper.level.getEntities(TechniqueProjectiles.ORB) { it.getOwner() === player }.single()
        val replacement = helper.caster().apply { uuid = player.uuid }
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(orb.deltaMovement == Vec3.ZERO && !orb.isRemoved, "Blue must settle first")
            }.thenExecute {
                player.discard()
                helper.level.addNewPlayer(replacement)
                helper.assertTrue(orb.getOwner() === replacement, "Vanilla ownership resolves the replacement UUID")
            }.thenExecuteAfter(2) {
                helper.assertTrue(orb.isRemoved, "A settled field must not inherit a replacement caster")
                replacement.discard()
            }.thenSucceed()
    }

    private companion object {
        val ORIGIN = Vec3(14.5, 10.0, 9.5)
        val TARGET = BlockPos(14, 10, 24)
        const val HEALTH = 500f
    }
}
