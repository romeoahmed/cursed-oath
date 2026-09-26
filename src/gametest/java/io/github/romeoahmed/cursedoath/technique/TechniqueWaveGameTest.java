package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.combat.CursedEnergy;
import io.github.romeoahmed.cursedoath.combat.SorcererAttachments;
import io.github.romeoahmed.cursedoath.combat.SorcererProfile;
import io.github.romeoahmed.cursedoath.domain.Domains;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TechniqueWaveGameTest {
    private static final BlockPos DENSE_MIN = new BlockPos(8, 6, 6),
            DENSE_MAX = new BlockPos(20, 16, 24),
            TARGET = new BlockPos(14, 10, 21);
    private static final Vec3 ORIGIN = new Vec3(14.5, 10.0, 9.5);
    private static final int FLIGHT_TICKS = 3;
    private static final float HEALTH = 500;

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void purpleLeavesItsCasterAndLaunchFootingIntact(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        var footing = new BlockPos(14, 9, 9);
        var forward = new BlockPos(14, 9, 15);
        helper.setBlock(footing, Blocks.STONE);
        helper.setBlock(forward, Blocks.STONE);
        var wave = launchWave(helper, player, Technique.PURPLE);
        helper.assertTrue(
                wave.position().subtract(player.getEyePosition()).dot(player.getLookAngle()) > 0,
                "The projectile starts at the forward casting position");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, forward);
            helper.assertBlockPresent(Blocks.STONE, footing);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Purple cannot hit its caster");
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena")
    public void purpleKeepsShellCollisionsInItsFlightLevelAfterCasterTransfer(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var defender = caster(helper);
        defender.setPos(player.getEyePosition().add(0, 0, 25));
        var domain = Domains.open(defender, Technique.UNLIMITED_VOID);
        var wave = launchWave(helper, player, Technique.PURPLE);
        var otherLevel = requireNonNull(helper.getLevel().getServer().getLevel(Level.NETHER));
        // Isolate the level handoff from teleport loading and connection side effects.
        player.setServerLevel(otherLevel);
        try {
            wave.tick();
            helper.assertTrue(domain.isRemoved(), "Independent Purple must hit shells in the projectile's world");
        } finally {
            player.setServerLevel(helper.getLevel());
            wave.discard();
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:flight", maxTicks = 100)
    public void purplePiercesWallsAndHitsEachTargetOnce(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(HEALTH);
        target.setHealth(HEALTH);
        var wall = new BlockPos(14, 11, 18);
        helper.setBlock(wall, Blocks.STONE);
        var work = requireNonNull(release(helper, player, Technique.PURPLE));
        helper.assertTrue(target.getHealth() == HEALTH, "Release must not cause instant ray damage");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        target.getHealth() < HEALTH, "Purple must reach the target independently of excavation"))
                .thenWaitUntil(() -> helper.assertTrue(work.finished(), "Flight and excavation must finish"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            target.getHealth() == HEALTH - TechniqueTuning.PURPLE_DAMAGE,
                            "The completed flight must damage the target exactly once; health=" + target.getHealth());
                    helper.assertBlockPresent(Blocks.AIR, wall);
                })
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:flight", maxTicks = 100)
    public void bedrockIsPreservedWithoutShieldingTargets(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var wall = new BlockPos(14, 11, 18);
        helper.setBlock(wall, Blocks.BEDROCK);
        var target = stationaryTarget(helper, EntityTypes.HUSK, TARGET);
        var before = target.getHealth();
        var work = requireNonNull(release(helper, player, Technique.PURPLE));
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "Flight and queued excavation must finish before checking preservation");
            helper.assertBlockPresent(Blocks.BEDROCK, wall);
            helper.assertTrue(target.getHealth() < before, "A preserved block must not shield entities");
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:flight", maxTicks = 100)
    public void containersAndFluidsSurviveCompletedPurpleExcavation(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var chest = new BlockPos(14, 11, 18);
        var fluid = chest.east();
        var behind = chest.above().south(2);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(fluid, Blocks.WATER);
        helper.setBlock(behind, Blocks.STONE);
        var work = requireNonNull(release(helper, player, Technique.PURPLE));
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "The entire flight and its queued excavation must finish");
            helper.assertBlockPresent(Blocks.CHEST, chest);
            helper.assertBlockPresent(Blocks.WATER, fluid);
            helper.assertBlockPresent(Blocks.AIR, behind);
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void fabricProtectionStopsTheCut(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var wall = new BlockPos(14, 11, 18);
        var absolute = helper.absolutePos(wall);
        beforeBlockBreak(helper, player, (level, actor, pos, state, entity) -> !pos.equals(absolute));
        helper.setBlock(wall, Blocks.STONE);
        var wave = launchWave(helper, player, Technique.DISMANTLE);
        helper.succeedWhen(() -> {
            helper.assertTrue(wave.isRemoved(), "A Fabric cancellation must stop the travelling cut");
            helper.assertBlockPresent(Blocks.STONE, wall);
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void dismantleCutsAWideThinOpening(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var cut = new BlockPos(18, 11, 18);
        var above = cut.above();
        helper.setBlock(cut, Blocks.STONE);
        helper.setBlock(above, Blocks.STONE);
        var target = stationaryTarget(helper, EntityTypes.HUSK, TARGET);
        var before = target.getHealth();
        var wave = launchWave(helper, player, Technique.DISMANTLE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, cut);
            helper.assertTrue(target.getHealth() < before, "The released cut must deal damage");
            helper.assertBlockPresent(Blocks.STONE, above);
            wave.discard();
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 120)
    public void peripheralBedrockDoesNotShieldTerrain(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var protectedPos = new BlockPos(18, 11, 18);
        var behind = protectedPos.south(2);
        helper.setBlock(protectedPos, Blocks.BEDROCK);
        helper.setBlock(behind, Blocks.STONE);
        var exposed = stationaryTarget(helper, EntityTypes.HUSK, TARGET);
        var wave = launchWave(helper, player, Technique.PURPLE);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    exposed.getHealth() < exposed.getMaxHealth(), "Clear paths must still receive Purple damage");
            helper.assertBlockPresent(Blocks.BEDROCK, protectedPos);
            helper.assertBlockPresent(Blocks.AIR, behind);
            wave.discard();
        });
    }

    @GameTest(environment = "cursed-oath-test:waves")
    @SuppressWarnings("ReferenceEquality") // Verify live entity ownership, not equality by entity ID.
    public void purpleRequiresReversalAndCancellationReleasesItsReservation(GameTestHelper helper) {
        var player = caster(helper);
        var fighter = fighter(helper, player);
        fighter.prepare(Technique.PURPLE);
        helper.assertTrue(fighter.cast() == null, "Purple must require reversal training");
        player.setAttached(SorcererAttachments.PROFILE, SorcererProfile.practiceProfile());
        fighter.prepare(Technique.PURPLE);
        var work = requireNonNull(requireNonNull(fighter.cast()).terrain());
        fighter.cancel();
        helper.assertTrue(work.finished(), "Cancelling must free terrain capacity");
        helper.assertTrue(fighter.energy().reserved() == 0, "Cancelling must release reserved energy");
        helper.assertTrue(
                fighter.energy().current()
                        == CursedEnergy.CAPACITY - Technique.PURPLE.cost().startup(),
                "Startup remains spent");
        helper.assertTrue(
                helper.getLevel()
                        .getEntities(TechniqueProjectiles.WAVE, wave -> wave.getOwner() == player)
                        .isEmpty(),
                "Cancellation must not spawn a wave");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void queuedExcavationChecksTheCurrentBlock(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var pos = new BlockPos(14, 11, 18);
        var absolute = helper.absolutePos(pos);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(absolute), 1.0);
        helper.setBlock(pos, Blocks.CHEST);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "The queued operation must settle");
            helper.assertBlockPresent(Blocks.CHEST, pos);
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void purpleRespectsTotemAndDoesNotSpendTheSameContactTwice(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        target.setItemSlot(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.getDefaultInstance());
        var wave = launchWave(helper, player, Technique.PURPLE);
        for (int tick = 0; tick < FLIGHT_TICKS; tick++) wave.tick();
        helper.assertTrue(target.getOffhandItem().isEmpty(), "Lethal Purple must invoke native totem protection");
        helper.assertTrue(target.isAlive(), "The totem must revive the target");
        // Re-enter this wave without vanilla damage immunity or interference from other test arenas.
        target.setInvulnerableTime(0);
        target.setPos(wave.position());
        wave.tick();
        helper.assertTrue(target.isAlive(), "The same Purple must not kill the revived target again");
        wave.discard();
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void purpleMaintainsSpeedThroughDenseTerrainAfterCasterRemoval(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        BlockPos.betweenClosed(DENSE_MIN, DENSE_MAX).forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, TARGET);
        var wave = launchWave(helper, player, Technique.PURPLE);
        var start = wave.position();
        player.discard();
        for (int tick = 0; tick < FLIGHT_TICKS; tick++) wave.tick();
        helper.assertTrue(
                wave.position().distanceTo(start) == TechniqueTuning.PURPLE_SPEED * FLIGHT_TICKS,
                "Terrain density and caster removal must not change flight speed");
        helper.assertTrue(
                target.getHealth() < target.getMaxHealth(), "Flight must still damage targets inside terrain");
        wave.discard();
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, new BlockPos(14, 11, 18)));
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void purplePreservesDeniedBlocksButExcavatesBehindThem(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var denied = new BlockPos(14, 11, 18);
        var behind = denied.south(2);
        var absolute = helper.absolutePos(denied);
        beforeBlockBreak(helper, player, (level, actor, pos, state, entity) -> !pos.equals(absolute));
        helper.setBlock(denied, Blocks.STONE);
        helper.setBlock(behind, Blocks.STONE);
        var wave = launchWave(helper, player, Technique.PURPLE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, behind);
            helper.assertBlockPresent(Blocks.STONE, denied);
            wave.discard();
        });
    }
}
