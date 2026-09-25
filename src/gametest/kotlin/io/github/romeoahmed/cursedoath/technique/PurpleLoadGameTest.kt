package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.reserveTerrain
import io.github.romeoahmed.cursedoath.stationaryTarget
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class PurpleLoadGameTest {
    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 300)
    fun concurrentFlightsDrainTheirTerrainAfterRemoval(helper: GameTestHelper) {
        BlockPos.betweenClosed(WALL_MIN, WALL_MAX).forEach { helper.setBlock(it, Blocks.STONE) }
        val targets =
            (0..<TARGETS).map { index ->
                helper
                    .stationaryTarget(
                        EntityTypes.VILLAGER,
                        BlockPos(FIRST_X + index % COLUMNS * SPACING, HEIGHT, TARGET_Z),
                    ).apply {
                        checkNotNull(getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
                        health = HEALTH
                    }
            }
        val work =
            (0..<COLUMNS).map { column ->
                val player =
                    helper.caster().apply {
                        setPos(helper.absoluteVec(Vec3(FIRST_X + column * SPACING + 0.5, HEIGHT.toDouble(), START_Z)))
                    }
                checkNotNull(helper.reserveTerrain(player)).also {
                    TechniqueProjectiles.release(player, Technique.PURPLE, it)
                }
            }
        helper.succeedWhen {
            helper.assertTrue(targets.all { it.health < HEALTH }, "All lanes must receive swept damage")
            helper.assertTrue(work.all { it.finished }, "Accepted terrain must drain after its flight disappears")
            for (column in 0..<COLUMNS) {
                helper.assertBlockPresent(Blocks.AIR, BlockPos(FIRST_X + column * SPACING, HEIGHT, TARGET_Z))
            }
        }
    }

    private companion object {
        val WALL_MIN = BlockPos(2, 6, 12)
        val WALL_MAX = BlockPos(29, 16, 28)
        const val COLUMNS = 4
        const val TARGETS = 32
        const val FIRST_X = 5
        const val SPACING = 6
        const val HEIGHT = 10
        const val START_Z = 9.5
        const val TARGET_Z = 21
        const val HEALTH = 500f
    }
}
