package io.github.romeoahmed.cursedoath.technique;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.domain.Domains;
import java.util.List;
import java.util.UUID;
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
public final class TechniqueImpactGameTest {
    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena")
    public void cleaveCancellationDuringContactDoesNotScheduleALattice(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = durableTarget(helper, new BlockPos(14, 10, 11));
        var work = requireNonNull(reserveTerrain(helper, player));
        allowDamage(helper, (entity, source, amount) -> {
            if (entity.equals(target)) work.close();
            return true;
        });
        var contact = CleaveContact.release(player, work);
        helper.assertTrue(target.getHealth() < HEALTH, "Initial contact must reach the callback");
        helper.assertTrue(contact == null && work.finished(), "Canceled contact cannot enqueue deferred cutting");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void orbFieldsCannotReachThroughAnIntactShell(GameTestHelper helper) {
        var caster = caster(helper);
        var attacker = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        target.setPos(domain.position().add(0.0, domain.radius() - 2, 0.0));
        var impact = domain.position().add(0.0, domain.radius() + 1, 0.0);
        var health = target.getHealth();
        BlueField.tick(attacker, impact, 1);
        RedBlast.impact(attacker, impact, new Vec3(0.0, -1.0, 0.0));
        helper.assertTrue(target.getHealth() == health, "The shell must stop secondary damage");
        helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "The shell must stop secondary forces");
        Domains.end(domain);
        BlueField.tick(attacker, impact, 1);
        helper.assertTrue(target.getDeltaMovement().y > 0, "The same field reaches its target after collapse");
        helper.succeed();
    }

    private static final Vec3 ORIGIN = new Vec3(14.5, 10.0, 9.5);
    private static final BlockPos TARGET = new BlockPos(14, 10, 14);
    private static final int SETTLE_TICKS = 20, MIN_CLEAVE_BLOCKS = 100;
    private static final float HEALTH = 500;

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    @SuppressWarnings("ReferenceEquality") // Verify live entity ownership, not equality by entity ID.
    public void blueDeliversItsCompleteCompressionOutput(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        helper.getLevel().addNewPlayer(player);
        var target = durableTarget(helper, TARGET);
        release(helper, player, Technique.BLUE);
        var orbs = helper.getLevel().getEntities(TechniqueProjectiles.ORB, orb -> orb.getOwner() == player);
        helper.assertTrue(orbs.size() == 1, "Release must create exactly one owned orb");
        var orb = orbs.getFirst();
        orb.setPos(target.getBoundingBox().getCenter());
        orb.setDeltaMovement(Vec3.ZERO);
        helper.runAfterDelay(TechniqueTuning.BLUE_DURATION + 1L, () -> {
            player.discard();
            helper.assertTrue(orb.isRemoved(), "The visible core must expire with its attraction");
            helper.assertTrue(
                    target.getHealth() == HEALTH - TechniqueTuning.BLUE_OUTPUT, "Blue must deliver six core pulses");
            helper.succeed();
        });
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena")
    public void redPeakDoublesCompleteBlueOutput(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = durableTarget(helper, TARGET);
        RedBlast.impact(player, target.getBoundingBox().getCenter(), player.getLookAngle());
        helper.assertTrue(
                target.getHealth() == HEALTH - TechniqueTuning.BLUE_OUTPUT * 2,
                "Uncovered Red must double Blue output; health=" + target.getHealth());
        helper.assertTrue(target.getDeltaMovement().z > 3.0, "Red must deliver its stronger directional impulse");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void cleaveExcavatesIntersectingCutsAtContact(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var work = requireNonNull(reserveTerrain(helper, player));
        var wall = BlockPos.betweenClosedStream(new BlockPos(9, 7, 11), new BlockPos(19, 17, 14))
                .map(BlockPos::immutable)
                .toList();
        wall.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "Cleave terrain work must settle");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(14, 11, 11));
            var removed = wall.stream()
                    .filter(pos -> helper.getBlockState(pos).isAir())
                    .count();
            helper.assertTrue(removed > MIN_CLEAVE_BLOCKS, "Contact cutting must open a substantial lattice");
            helper.assertTrue(removed < wall.size(), "Cleave must preserve material between the cutting planes");
        });
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void cleaveAdaptsToDurabilityWithoutRepeatingTheInitialHit(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var target = durableTarget(helper, new BlockPos(14, 10, 11));
        var work = requireNonNull(reserveTerrain(helper, player));
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work);
        helper.assertTrue(
                target.getHealth() == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE,
                "Cleave must adapt up to its output cap");
        helper.assertTrue(
                HEALTH - target.getHealth() > TechniqueTuning.DISMANTLE_DAMAGE, "Contact Cleave must exceed Dismantle");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(work.finished(), "The contact lattice must finish"))
                .thenExecuteAfter(
                        SETTLE_TICKS,
                        () -> helper.assertTrue(
                                target.getHealth() == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE,
                                "The initial target is hit once"))
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void cleaveCutsThroughStoneAndDamagesMultipleTargetsOnce(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        BlockPos.betweenClosed(new BlockPos(8, 5, 11), new BlockPos(21, 18, 12))
                .forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        var targets = List.of(
                durableTarget(helper, new BlockPos(12, 10, 15)), durableTarget(helper, new BlockPos(16, 10, 15)));
        var work = requireNonNull(reserveTerrain(helper, player));
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work);
        helper.assertTrue(
                targets.stream().allMatch(target -> target.getHealth() == HEALTH), "Damage must wait for excavation");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(work.finished(), "The lattice must finish its terrain work"))
                .thenExecuteAfter(
                        SETTLE_TICKS,
                        () -> helper.assertTrue(
                                targets.stream()
                                        .allMatch(target ->
                                                target.getHealth() == HEALTH - TechniqueTuning.CLEAVE_MAX_DAMAGE),
                                "Every exposed target must receive one adaptive hit, including behind the former wall"))
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:power", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void cleavePreservesProtectedColumnsAndGridGaps(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        helper.setBlock(new BlockPos(14, 11, 11), Blocks.STONE);
        var shield = new BlockPos(16, 11, 13);
        helper.setBlock(shield, Blocks.BEDROCK);
        var shielded = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(16, 10, 15));
        var gap = stationaryTarget(helper, EntityTypes.RABBIT, new BlockPos(15, 12, 15));
        var work = requireNonNull(reserveTerrain(helper, player));
        TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "The lattice must settle");
            helper.assertBlockPresent(Blocks.BEDROCK, shield);
            helper.assertTrue(
                    shielded.getHealth() == shielded.getMaxHealth(), "A protected column must shield its far side");
            helper.assertTrue(
                    gap.getHealth() == gap.getMaxHealth(), "A target between cutting planes must remain untouched");
        });
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void removingPurpleDuringDamageStopsRemainingContacts(GameTestHelper helper) {
        removalDuringDamage(helper, Technique.PURPLE);
    }

    @GameTest(environment = "cursed-oath-test:waves", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void removingDismantleDuringDamageStopsRemainingContacts(GameTestHelper helper) {
        removalDuringDamage(helper, Technique.DISMANTLE);
    }

    private static void removalDuringDamage(GameTestHelper helper, Technique technique) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(ORIGIN));
        var targets = List.of(
                stationaryTarget(helper, EntityTypes.VILLAGER, TARGET),
                stationaryTarget(helper, EntityTypes.VILLAGER, TARGET));
        var wave = launchWave(helper, player, technique);
        allowDamage(helper, (target, source, amount) -> {
            if (targets.contains(target)) wave.discard();
            return true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(wave.isRemoved(), "The damage callback must remove the wave");
            helper.assertTrue(
                    targets.stream()
                                    .filter(target -> target.getHealth() < target.getMaxHealth())
                                    .count()
                            == 1,
                    "Removal must stop contacts later in the same tick");
        });
    }

    private static Villager durableTarget(GameTestHelper helper, BlockPos pos) {
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, pos);
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(HEALTH);
        target.setHealth(HEALTH);
        return target;
    }
}
