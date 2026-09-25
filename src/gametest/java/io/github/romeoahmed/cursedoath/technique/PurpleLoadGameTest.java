package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PurpleLoadGameTest {
    private static final BlockPos WALL_MIN = new BlockPos(2, 6, 12), WALL_MAX = new BlockPos(29, 16, 28);
    private static final int COLUMNS = 4, TARGETS = 32, FIRST_X = 5, SPACING = 6, HEIGHT = 10, TARGET_Z = 21;
    private static final double START_Z = 9.5;
    private static final float HEALTH = 500;

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 300)
    public void concurrentFlightsDrainTheirTerrainAfterRemoval(GameTestHelper helper) {
        BlockPos.betweenClosed(WALL_MIN, WALL_MAX).forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        var targets = new ArrayList<Villager>();
        for (int index = 0; index < TARGETS; index++) {
            var target = stationaryTarget(
                    helper, EntityTypes.VILLAGER, new BlockPos(FIRST_X + index % COLUMNS * SPACING, HEIGHT, TARGET_Z));
            requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(HEALTH);
            target.setHealth(HEALTH);
            targets.add(target);
        }
        var work = new ArrayList<TerrainDestruction.Work>();
        for (int column = 0; column < COLUMNS; column++) {
            var player = caster(helper);
            player.setPos(helper.absoluteVec(new Vec3(FIRST_X + column * SPACING + 0.5, HEIGHT, START_Z)));
            var reservation = requireNonNull(reserveTerrain(helper, player));
            work.add(reservation);
            TechniqueProjectiles.release(player, Technique.PURPLE, reservation);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    targets.stream().allMatch(target -> target.getHealth() < HEALTH),
                    "All lanes must receive swept damage");
            helper.assertTrue(
                    work.stream().allMatch(TerrainDestruction.Work::finished),
                    "Accepted terrain must drain after its flight disappears");
            for (int column = 0; column < COLUMNS; column++)
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(FIRST_X + column * SPACING, HEIGHT, TARGET_Z));
        });
    }
}
