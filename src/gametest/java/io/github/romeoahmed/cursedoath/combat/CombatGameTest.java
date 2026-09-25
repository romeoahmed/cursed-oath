package io.github.romeoahmed.cursedoath.combat;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.technique.CleaveContact;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles;
import io.github.romeoahmed.cursedoath.technique.TechniqueWave;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CombatGameTest {
    @GameTest(environment = "cursed-oath-test:combat")
    public void nativePrimaryHitAppliesBlackFlashPowerAndConsumesPreparation(GameTestHelper helper) {
        var player = caster(helper);
        CombatRuntime.practice(player, true);
        var fighter = CombatRuntime.fighter(player);
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 3));
        float health = 100;
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(health);
        target.setHealth(health);
        requireNonNull(player.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(4.0);
        for (int tick = 0; tick < 20; tick++) player.doTick();
        fighter.preparePulse();
        // Fix only the random roll; native attack, damage events and the mixin still run.
        player.getRandom().setSeed(4096L);
        player.attack(target);
        helper.assertTrue(target.getHealth() == health - 32, "Native Black Flash applies 4^2.5 damage");
        helper.assertTrue(!fighter.consumePulse(), "The attack consumes preparation exactly once");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void unavailableFlightEndsWithoutLoadingChunksOrLeakingTerrain(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(new Vec3(10_000.5, 10.0, 10_000.5)));
        var chunk = ChunkPos.containing(player.blockPosition());
        helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                "Fixture must be unloaded");
        for (var technique : List.of(Technique.BLUE, Technique.RED, Technique.PURPLE, Technique.DISMANTLE)) {
            var work = requireNonNull(reserveTerrain(helper, player));
            Entity projectile;
            if (technique == Technique.BLUE || technique == Technique.RED) {
                var orb = new TechniqueOrb(TechniqueProjectiles.ORB, helper.getLevel());
                orb.configure(player, technique, work);
                projectile = orb;
            } else {
                var wave = new TechniqueWave(TechniqueProjectiles.WAVE, helper.getLevel());
                wave.configure(player, technique, work);
                projectile = wave;
            }
            // These entities are deliberately unregistered: only this call advances them.
            projectile.tick();
            helper.assertTrue(projectile.isRemoved() && work.finished(), technique + " must release its terrain slot");
        }
        helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                "Flight must not load chunks");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void cleaveDoesNotLoadChunksForItsContactRay(GameTestHelper helper) {
        var player = caster(helper);
        player.setPos(helper.absoluteVec(new Vec3(10_000.5, 10.0, 10_000.5)));
        var chunk = ChunkPos.containing(player.blockPosition());
        helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                "Fixture must be unloaded");
        var work = requireNonNull(reserveTerrain(helper, player));
        helper.assertTrue(
                CleaveContact.release(player, work) == null && work.finished(), "Unavailable contact is rejected");
        helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                "Contact checks must not synchronously load a chunk");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat", structure = "cursed-oath-test:arena")
    public void cleaveMissHasNoImpactAndReleasesTerrainCapacity(GameTestHelper helper) {
        var player = caster(helper);
        var work = requireNonNull(reserveTerrain(helper, player));
        helper.assertTrue(CleaveContact.release(player, work) == null, "A miss must not publish a successful impact");
        helper.assertTrue(work.finished(), "A miss must free the terrain reservation");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat", structure = "cursed-oath-test:arena")
    public void cleaveUsesFourBlockSurfaceReach(GameTestHelper helper) {
        var player = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 6));
        var halfWidth = target.getBoundingBox().getZsize() / 2;
        target.setPos(player.position().add(0.0, 0.0, 4.1 + halfWidth));
        var miss = requireNonNull(reserveTerrain(helper, player));
        helper.assertTrue(
                CleaveContact.release(player, miss) == null, "The real surface beyond reach must be rejected");
        target.setPos(player.position().add(0.0, 0.0, 3.9 + halfWidth));
        try (var hit = requireNonNull(reserveTerrain(helper, player))) {
            helper.assertTrue(CleaveContact.release(player, hit) != null, "Contact near four blocks must be accepted");
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "Accepted contact must cause damage");
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 40)
    public void healingChecksQualificationAndCommitsOnlyAfterPreparation(GameTestHelper helper) {
        var player = caster(helper);
        var fighter = fighter(helper, player);
        player.setHealth(10);
        fighter.prepare(Technique.HEAL);
        helper.assertTrue(fighter.cast() == null, "Self-healing requires reversal training");
        player.setAttached(SorcererAttachments.PROFILE, SorcererProfile.practiceProfile());
        fighter.prepare(Technique.HEAL);
        helper.assertTrue(player.getHealth() == 10, "Preparation must not heal early");
        helper.onEachTick(fighter::tick);
        helper.succeedWhen(() -> {
            helper.assertTrue(fighter.cast() == null, "Healing must finish preparation");
            helper.assertTrue(player.getHealth() == 16, "Healing must use the native health boundary");
            var spent = Technique.HEAL.cost().startup() + Technique.HEAL.cost().release();
            helper.assertTrue(
                    fighter.energy().current() == CursedEnergy.CAPACITY - spent, "Release is paid exactly once");
            helper.assertTrue(fighter.energy().reserved() == 0 && fighter.recovery() > 0, "Recovery survives release");
        });
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void contactCannotReachThroughAThinBarrier(GameTestHelper helper) {
        var player = caster(helper);
        // The vertical south-facing trapdoor occupies z=3..3.1875.
        var wall = Blocks.OAK_TRAPDOOR
                .defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH);
        var wallPos = new BlockPos(2, 2, 3);
        helper.setBlock(wallPos, wall);
        var target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, wallPos.below());
        target.setPos(helper.absoluteVec(new Vec3(2.5, 1.0, 3.6)));
        var hit = TechniqueCombat.contact(player, 3.0);
        helper.assertTrue(
                hit != null && hit.getType() == HitResult.Type.BLOCK,
                "Contact must stop at the thin barrier before reaching the target");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void cleaveHitsAnOverlappingTarget(GameTestHelper helper) {
        var player = caster(helper);
        var target = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 1));
        var before = target.getHealth();
        try (var work = requireNonNull(reserveTerrain(helper, player))) {
            TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, work);
        }
        helper.assertTrue(target.getHealth() < before, "A ray starting inside a target must still hit");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void switchingToSpectatorCancelsWithoutRefundingStartup(GameTestHelper helper) {
        var player = caster(helper);
        var fighter = fighter(helper, player);
        fighter.prepare(Technique.BLUE);
        player.setGameMode(GameType.SPECTATOR);
        fighter.tick();
        helper.assertTrue(fighter.cast() == null, "Spectators must not finish a prepared cast");
        helper.assertTrue(fighter.energy().reserved() == 0, "Cancellation must release the reservation");
        helper.assertTrue(
                fighter.energy().current()
                        == CursedEnergy.CAPACITY - Technique.BLUE.cost().startup(),
                "Only startup energy should remain spent");
        helper.assertTrue(fighter.recovery() > 0, "Cancellation must retain recovery");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void cancellationAndReconnectPreserveResourceObligations(GameTestHelper helper) {
        var player = caster(helper);
        var fighter = fighter(helper, player);
        fighter.prepare(Technique.BLUE);
        var remaining = fighter.recovery();
        fighter.cancel();
        var reconnected = fighter(helper, player);
        helper.assertTrue(reconnected.energy().equals(fighter.energy()), "Reconnection must not refill energy");
        helper.assertTrue(reconnected.recovery() == remaining, "Reconnection must retain recovery");
        helper.assertTrue(reconnected.cast() == null, "Reservations must not be restored as active casts");
        helper.succeed();
    }
}
