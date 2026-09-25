package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RedBlastGameTest {
    private static final Vec3 IMPACT = new Vec3(2.5, 2.5, 3.5);

    @GameTest(structure = "cursed-oath-test:arena")
    public void redDamagesAndRepelsAnOffAxisTarget(GameTestHelper helper) {
        var player = caster(helper);
        var target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(3, 1, 4));
        target.setOnGround(true);
        var before = target.getHealth();
        RedBlast.impact(player, helper.absoluteVec(IMPACT), player.getLookAngle());
        helper.assertTrue(target.getHealth() < before, "The impact must reach an exposed off-axis target");
        helper.assertTrue(target.getDeltaMovement().z > 0.5, "Red must repel the target in the casting direction");
        helper.assertTrue(target.getDeltaMovement().y > 0, "The impact must lift grounded targets");
        helper.assertTrue(target.syncVelocity, "The impulse must synchronize to tracking clients and the target");
        helper.succeed();
    }

    @GameTest(structure = "cursed-oath-test:arena")
    public void redAffectsExposedNeighborsBeforeExcavation(GameTestHelper helper) {
        var player = caster(helper);
        var direct = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 4));
        var neighbor = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(4, 1, 4));
        var far = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(14, 1, 4));
        var marker = new BlockPos(2, 0, 4);
        helper.setBlock(marker, Blocks.STONE);
        var before = neighbor.getHealth();
        var farHealth = far.getHealth();
        RedBlast.impact(player, helper.absoluteVec(IMPACT), player.getLookAngle());
        helper.assertTrue(direct.getHealth() < direct.getMaxHealth(), "The direct impact must cause damage");
        helper.assertTrue(neighbor.getHealth() < before, "The exposed neighbor must receive the impact wave");
        helper.assertTrue(far.getHealth() == farHealth, "Red must remain bounded by its impact radius");
        helper.assertBlockPresent(Blocks.STONE, marker);
        helper.succeed();
    }

    @GameTest(structure = "cursed-oath-test:arena")
    public void redRespectsFullCoverAndKnockbackResistance(GameTestHelper helper) {
        var player = caster(helper);
        var shielded = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 6));
        for (int x = 0; x <= 5; x++) for (int y = 0; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
        var before = shielded.getHealth();
        RedBlast.impact(player, helper.absoluteVec(IMPACT), player.getLookAngle());
        helper.assertTrue(shielded.getHealth() == before, "A full wall must block the impact wave");
        helper.assertTrue(
                shielded.getDeltaMovement().equals(Vec3.ZERO), "Cover must prevent knockback as well as damage");
        var resistant = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 3));
        requireNonNull(resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(1.0);
        RedBlast.impact(player, helper.absoluteVec(IMPACT), player.getLookAngle());
        helper.assertTrue(
                resistant.getHealth() < resistant.getMaxHealth(),
                "Knockback resistance must not imply damage immunity");
        helper.assertTrue(
                resistant.getDeltaMovement().equals(Vec3.ZERO), "Repulsion must respect full knockback resistance");
        helper.succeed();
    }

    @GameTest(structure = "cursed-oath-test:arena")
    public void redRepelsAlongAVerticalCast(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(player.position().add(0.0, 5.0, 0.0));
        player.setXRot(90);
        var target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 1));
        RedBlast.impact(player, target.getBoundingBox().getCenter(), player.getLookAngle());
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "Downward Red must hit");
        helper.assertTrue(target.getDeltaMovement().y < 0, "Red must preserve the vertical casting direction");
        helper.succeed();
    }
}
