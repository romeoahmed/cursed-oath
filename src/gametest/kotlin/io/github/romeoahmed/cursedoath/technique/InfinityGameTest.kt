package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class InfinityGameTest {
    @GameTest(environment = "cursed-oath-test:domains", structure = "cursed-oath-test:arena")
    fun nativeArrowApproachesAndStopsEvenWhenItsPathStartsOutsideTheScan(helper: GameTestHelper) {
        val defender = helper.caster()
        defender.setPos(helper.absoluteVec(DEFENDER))
        CombatRuntime.practice(defender, true)
        CombatRuntime.fighter(defender).prepare(Technique.INFINITY)
        val arrow = helper.spawn(EntityTypes.ARROW, BlockPos(1, 6, 20))
        val center = defender.boundingBox.center
        try {
            arrow.setNoGravity(true)
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0))
            arrow.deltaMovement = Vec3(SPEED, 0.0, 0.0)
            arrow.tick()
            helper.assertTrue(arrow.x < center.x && !arrow.isRemoved, "A high-speed arrow cannot skip Infinity")
            val entry = arrow.position().distanceTo(center)
            helper.assertTrue(entry <= SLOW_RADIUS + EPSILON, "The arrow visibly reaches the slowing region")
            repeat(SETTLE_TICKS) { arrow.tick() }
            val stopped = arrow.position().distanceTo(center)
            helper.assertTrue(
                stopped < entry && stopped >= STOP_RADIUS - EPSILON,
                "Approach converges outside the body: entry=$entry, stopped=$stopped",
            )
            helper.assertTrue(arrow.deltaMovement.lengthSqr() < EPSILON, "The arrow stalls instead of reflecting")
        } finally {
            arrow.discard()
            CombatRuntime.practice(defender, false)
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains", structure = "cursed-oath-test:arena")
    fun infinityPreservesOwnShotsAndNativeWalls(helper: GameTestHelper) {
        val defender = helper.caster()
        defender.setPos(helper.absoluteVec(DEFENDER))
        CombatRuntime.practice(defender, true)
        CombatRuntime.fighter(defender).prepare(Technique.INFINITY)
        val arrow = helper.spawn(EntityTypes.ARROW, BlockPos(1, 6, 20))
        val center = defender.boundingBox.center
        try {
            arrow.setNoGravity(true)
            arrow.owner = defender
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0))
            arrow.deltaMovement = Vec3(OWN_SPEED, 0.0, 0.0)
            arrow.tick()
            helper.assertTrue(arrow.x > center.x, "The caster's own projectile is not stopped")
            arrow.owner = null
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0))
            helper.setBlock(WALL, Blocks.STONE)
            arrow.deltaMovement = Vec3(SPEED, 0.0, 0.0)
            arrow.tick()
            helper.assertTrue(
                arrow.x < helper.absolutePos(WALL.east()).x,
                "The wall before Infinity still intercepts",
            )
        } finally {
            arrow.discard()
            CombatRuntime.practice(defender, false)
        }
        helper.succeed()
    }

    private companion object {
        val WALL = BlockPos(5, 6, 20)
        val DEFENDER = Vec3(25.0, 6.0, 20.5)
        const val START_DISTANCE = 24.0
        const val SPEED = 48.0
        const val OWN_SPEED = 28.0
        const val SLOW_RADIUS = 4.1
        const val STOP_RADIUS = 1.1
        const val EPSILON = 0.001
        const val SETTLE_TICKS = 80
    }
}
