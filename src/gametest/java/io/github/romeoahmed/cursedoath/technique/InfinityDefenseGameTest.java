package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class InfinityDefenseGameTest {
    private static final BlockPos WALL = new BlockPos(5, 6, 20);
    private static final Vec3 DEFENDER = new Vec3(25.0, 6.0, 20.5);
    private static final double START_DISTANCE = 24.0,
            SPEED = 48.0,
            OWN_SPEED = 28.0,
            SLOW_RADIUS = 4.1,
            STOP_RADIUS = 1.1,
            EPSILON = 0.001;
    private static final int SETTLE_TICKS = 80;

    @GameTest(environment = "cursed-oath-test:domains", structure = "cursed-oath-test:arena")
    public void nativeArrowApproachesAndStopsEvenWhenItsPathStartsOutsideTheScan(GameTestHelper helper) {
        var defender = caster(helper);
        defender.setPos(helper.absoluteVec(DEFENDER));
        CombatRuntime.practice(defender, true);
        CombatRuntime.fighter(defender).prepare(Technique.INFINITY);
        var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(1, 6, 20));
        var center = defender.getBoundingBox().getCenter();
        try {
            arrow.setNoGravity(true);
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0));
            arrow.setDeltaMovement(new Vec3(SPEED, 0.0, 0.0));
            arrow.tick();
            helper.assertTrue(arrow.getX() < center.x && !arrow.isRemoved(), "A high-speed arrow cannot skip Infinity");
            var entry = arrow.position().distanceTo(center);
            helper.assertTrue(entry <= SLOW_RADIUS + EPSILON, "The arrow visibly reaches the slowing region");
            for (int tick = 0; tick < SETTLE_TICKS; tick++) arrow.tick();
            var stopped = arrow.position().distanceTo(center);
            helper.assertTrue(
                    stopped < entry && stopped >= STOP_RADIUS - EPSILON,
                    "Approach converges outside the body: entry=" + entry + ", stopped=" + stopped);
            helper.assertTrue(arrow.getDeltaMovement().lengthSqr() < EPSILON, "The arrow stalls instead of reflecting");
        } finally {
            arrow.discard();
            CombatRuntime.practice(defender, false);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains", structure = "cursed-oath-test:arena")
    public void infinityPreservesOwnShotsAndNativeWalls(GameTestHelper helper) {
        var defender = caster(helper);
        defender.setPos(helper.absoluteVec(DEFENDER));
        CombatRuntime.practice(defender, true);
        CombatRuntime.fighter(defender).prepare(Technique.INFINITY);
        var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(1, 6, 20));
        var center = defender.getBoundingBox().getCenter();
        try {
            arrow.setNoGravity(true);
            arrow.setOwner(defender);
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0));
            arrow.setDeltaMovement(new Vec3(OWN_SPEED, 0.0, 0.0));
            arrow.tick();
            helper.assertTrue(arrow.getX() > center.x, "The caster's own projectile is not stopped");
            arrow.setOwner(null);
            arrow.setPos(center.add(-START_DISTANCE, 0.0, 0.0));
            helper.setBlock(WALL, Blocks.STONE);
            arrow.setDeltaMovement(new Vec3(SPEED, 0.0, 0.0));
            arrow.tick();
            helper.assertTrue(
                    arrow.getX() < helper.absolutePos(WALL.east()).getX(), "The wall before Infinity still intercepts");
        } finally {
            arrow.discard();
            CombatRuntime.practice(defender, false);
        }
        helper.succeed();
    }
}
