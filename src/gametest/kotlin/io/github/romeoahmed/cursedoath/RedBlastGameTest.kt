package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.technique.RedBlast
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class RedBlastGameTest {
    @GameTest(structure = "cursed-oath-test:arena")
    fun redDamagesAndRepelsAnOffAxisTarget(helper: GameTestHelper) {
        val player = helper.caster()
        val target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(3, 1, 4))
        target.setOnGround(true)
        val before = target.health
        RedBlast.impact(
            player,
            helper.absoluteVec(IMPACT),
            player.lookAngle,
        )
        helper.assertTrue(target.health < before, "The impact must reach an exposed off-axis target")
        val minimumImpulse = 0.5
        helper.assertTrue(target.deltaMovement.z > minimumImpulse, "Red must repel the target in the casting direction")
        helper.assertTrue(target.deltaMovement.y > 0, "The impact must lift grounded targets")
        helper.assertTrue(target.syncVelocity, "The impulse must synchronize to tracking clients and the target")
        helper.succeed()
    }

    @GameTest(structure = "cursed-oath-test:arena")
    fun redAffectsExposedNeighborsBeforeExcavation(helper: GameTestHelper) {
        val player = helper.caster()
        val direct = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(2, 1, 4))
        val neighbor = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(4, 1, 4))
        val farX = 14
        val far = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(farX, 1, 4))
        val marker = BlockPos(2, 0, 4)
        helper.setBlock(marker, Blocks.STONE)
        val before = neighbor.health
        val farHealth = far.health
        RedBlast.impact(
            player,
            helper.absoluteVec(IMPACT),
            player.lookAngle,
        )
        helper.assertTrue(direct.health < direct.maxHealth, "The direct impact must cause damage")
        helper.assertTrue(neighbor.health < before, "The exposed neighbor must receive the impact wave")
        helper.assertTrue(far.health == farHealth, "Red must remain bounded by its impact radius")
        helper.assertBlockPresent(Blocks.STONE, marker)
        helper.succeed()
    }

    @GameTest(structure = "cursed-oath-test:arena")
    fun redRespectsFullCoverAndKnockbackResistance(helper: GameTestHelper) {
        val player = helper.caster()
        val behindWallZ = 6
        val shielded = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(2, 1, behindWallZ))
        val wallWidth = 5
        val wallHeight = 4
        val wallZ = 4
        for (x in 0..wallWidth) for (y in 0..wallHeight) helper.setBlock(BlockPos(x, y, wallZ), Blocks.STONE)
        val before = shielded.health
        RedBlast.impact(
            player,
            helper.absoluteVec(IMPACT),
            player.lookAngle,
        )
        helper.assertTrue(shielded.health == before, "A full wall must block the impact wave")
        helper.assertTrue(shielded.deltaMovement == Vec3.ZERO, "Cover must prevent knockback as well as damage")
        val resistant = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(2, 1, 3))
        checkNotNull(resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).baseValue = 1.0
        RedBlast.impact(
            player,
            helper.absoluteVec(IMPACT),
            player.lookAngle,
        )
        helper.assertTrue(resistant.health < resistant.maxHealth, "Knockback resistance must not imply damage immunity")
        helper.assertTrue(resistant.deltaMovement == Vec3.ZERO, "Repulsion must respect full knockback resistance")
        helper.succeed()
    }

    @GameTest(structure = "cursed-oath-test:arena")
    fun redRepelsAlongAVerticalCast(helper: GameTestHelper) {
        val player = helper.caster()
        val castingHeight = 5.0
        player.setPos(player.position().add(0.0, castingHeight, 0.0))
        val downwardPitch = 90f
        player.xRot = downwardPitch
        val target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, BlockPos(2, 1, 1))
        RedBlast.impact(
            player,
            target.boundingBox.center,
            player.lookAngle,
        )
        helper.assertTrue(target.health < target.maxHealth, "Downward Red must hit")
        helper.assertTrue(target.deltaMovement.y < 0, "Red must preserve the vertical casting direction")
        helper.succeed()
    }

    private companion object {
        val IMPACT = Vec3(2.5, 2.5, 3.5)
    }
}
