package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TechniqueOrbGameTest {
    private static final Vec3 ORIGIN = new Vec3(14.5, 10.0, 9.5);
    private static final BlockPos TARGET = new BlockPos(14, 10, 24);
    private static final float HEALTH = 500;

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena")
    public void blueStopsItsTargetBatchWhenTheCasterIsRemoved(GameTestHelper helper) {
        rangeEffectStopsOnOwnerRemoval(helper, Technique.BLUE);
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena")
    public void redStopsItsTargetBatchWhenTheCasterIsRemoved(GameTestHelper helper) {
        rangeEffectStopsOnOwnerRemoval(helper, Technique.RED);
    }

    private static void rangeEffectStopsOnOwnerRemoval(GameTestHelper helper, Technique technique) {
        var player = caster(helper);
        var first = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(13, 10, 12));
        var second = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(15, 10, 12));
        int[] hits = {0};
        allowDamage(helper, List.of(first, second), (entity, source, amount) -> {
            hits[0]++;
            player.discard();
            return true;
        });
        var center = helper.absoluteVec(new Vec3(14.5, 11, 12.5));
        if (technique == Technique.BLUE) {
            var orb = launchOrb(helper, player, technique);
            orb.setPos(center);
            orb.tickCount = 1;
            BlueField.tick(player, orb);
        } else RedBlast.impact(player, center, new Vec3(0, 0, 1));
        helper.assertTrue(hits[0] == 1, "Owner removal must stop the batch, regardless of target iteration order");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena")
    public void removingBlueDuringCompressionStopsRemainingTargets(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var targets = List.of(
                stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(14, 10, 12)),
                stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(14, 10, 12)));
        var orb = launchOrb(helper, player, Technique.BLUE);
        int[] hits = {0};
        allowDamage(helper, targets, (entity, source, amount) -> {
            hits[0]++;
            orb.discard();
            return true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hits[0] > 0, "Compression must reach the removal callback");
            helper.assertTrue(orb.isRemoved(), "The callback must remove the core");
            helper.assertTrue(hits[0] == 1, "A removed core cannot continue its attraction and damage batch");
        });
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void muzzleCannotSkipANearbyWall(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var wall = new BlockPos(14, 11, 10);
        helper.setBlock(wall, Blocks.BEDROCK);
        var orb = launchOrb(helper, player, Technique.BLUE);
        helper.succeedWhen(() -> {
            helper.assertTrue(orb.getDeltaMovement().equals(Vec3.ZERO), "The muzzle obstruction must settle Blue");
            helper.assertTrue(orb.getZ() <= helper.absolutePos(wall).getZ(), "The core must remain on the near side");
            helper.assertBlockPresent(Blocks.BEDROCK, wall);
        });
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void redRemovalDuringDamageDoesNotRescheduleClosedTerrain(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        var orb = launchOrb(helper, player, Technique.RED);
        allowDamage(helper, List.of(target), (entity, source, amount) -> {
            orb.discard();
            return true;
        });
        for (int tick = 0; tick < 20 && !orb.isRemoved(); tick++) orb.tick();
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "Red must reach the damage callback");
        helper.assertTrue(orb.isRemoved(), "The callback must remove Red without restarting its terrain work");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void redTravelsBeforeDamageAndDetonatesOnce(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(HEALTH);
        target.setHealth(HEALTH);
        var orb = launchOrb(helper, player, Technique.RED);
        helper.assertTrue(target.getHealth() == HEALTH, "Release must not deal remote damage");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            orb.getZ() > player.getZ() && !orb.isRemoved(),
                            "Red must occupy an intermediate flight position");
                    helper.assertTrue(
                            target.getHealth() == HEALTH, "Remote targets stay unharmed during initial flight");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(orb.isRemoved(), "Red must be removed after detonation");
                    helper.assertTrue(target.getHealth() < HEALTH, "The core must reach and damage the target");
                })
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void wallBuiltAfterReleaseInterceptsRed(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        var before = target.getHealth();
        var orb = launchOrb(helper, player, Technique.RED);
        BlockPos.betweenClosed(new BlockPos(11, 8, 13), new BlockPos(17, 15, 13))
                .forEach(pos -> helper.setBlock(pos, Blocks.BEDROCK));
        helper.succeedWhen(() -> {
            helper.assertTrue(orb.isRemoved(), "The new wall must intercept the travelling core");
            helper.assertTrue(target.getHealth() == before, "The intercepted attack must not damage the far side");
            helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(14, 11, 13));
        });
    }

    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void blueFliesThenSettlesAtTheNewObstacle(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        helper.getLevel().addNewPlayer(player);
        var orb = launchOrb(helper, player, Technique.BLUE);
        var wall = new BlockPos(14, 11, 14);
        helper.setBlock(wall, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            orb.getZ() > player.getZ()
                                    && !orb.getDeltaMovement().equals(Vec3.ZERO),
                            "Blue must launch and move");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(orb.getDeltaMovement().equals(Vec3.ZERO), "Blue must settle at contact");
                    helper.assertTrue(!orb.isRemoved(), "Settled Blue must remain as the visible compression field");
                    helper.assertBlockPresent(Blocks.AIR, wall);
                    orb.discard();
                    player.discard();
                })
                .thenSucceed();
    }

    @SuppressWarnings("ReferenceEquality") // Ownership must resolve the replacement instance, not just its entity ID.
    @GameTest(environment = "cursed-oath-test:orbs", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void settledBlueEndsWhenItsCasterIsReplaced(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        helper.getLevel().addNewPlayer(player);
        helper.setBlock(new BlockPos(14, 11, 14), Blocks.BEDROCK);
        var orb = launchOrb(helper, player, Technique.BLUE);
        var replacement = caster(helper);
        replacement.setUUID(player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            orb.getDeltaMovement().equals(Vec3.ZERO) && !orb.isRemoved(), "Blue must settle first");
                })
                .thenExecute(() -> {
                    player.discard();
                    helper.getLevel().addNewPlayer(replacement);
                    helper.assertTrue(orb.getOwner() == replacement, "Vanilla ownership resolves the replacement UUID");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(orb.isRemoved(), "A settled field must not inherit a replacement caster");
                    replacement.discard();
                })
                .thenSucceed();
    }
}
