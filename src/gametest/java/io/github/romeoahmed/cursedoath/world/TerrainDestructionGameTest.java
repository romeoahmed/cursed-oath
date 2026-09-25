package io.github.romeoahmed.cursedoath.world;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.combat.CursedEnergy;
import io.github.romeoahmed.cursedoath.combat.SorcererAttachments;
import io.github.romeoahmed.cursedoath.combat.SorcererProfile;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TerrainDestructionGameTest {
    @GameTest(environment = "cursed-oath-test:terrain")
    public void activeWorkStillOccupiesCapacityInsideBlockCallbacks(GameTestHelper helper) {
        var player = caster(helper);
        var position = new BlockPos(2, 1, 2);
        var absolute = helper.absolutePos(position);
        helper.setBlock(position, Blocks.STONE);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(absolute), 1);
        var reservations = new ArrayList<TerrainDestruction.Work>();
        var visited = new AtomicBoolean();
        var rejected = new AtomicBoolean();
        beforeBlockBreak(helper, player, (level, actor, pos, state, entity) -> {
            if (pos.equals(absolute)) {
                visited.set(true);
                rejected.set(reserveTerrain(helper, player) == null);
            }
            return true;
        });
        try {
            while (true) {
                var reservation = reserveTerrain(helper, player);
                if (reservation == null) break;
                reservations.add(reservation);
            }
            for (int tick = 0; tick < 10 && !visited.get(); tick++) TerrainDestruction.tick();
            helper.assertTrue(visited.get(), "The block callback must run");
            helper.assertTrue(rejected.get(), "Executing work must retain its slot during a native block callback");
        } finally {
            reservations.forEach(TerrainDestruction.Work::close);
            work.close();
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    public void callbackCancellationPreventsBlockRemoval(GameTestHelper helper) {
        var player = caster(helper);
        var position = new BlockPos(2, 1, 2);
        var absolute = helper.absolutePos(position);
        helper.setBlock(position, Blocks.STONE);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(absolute), 1.0);
        var completed = new AtomicBoolean();
        var canceled = new AtomicBoolean();
        work.onComplete(() -> completed.set(true));
        beforeBlockBreak(helper, player, (level, actor, pos, state, entity) -> {
            if (pos.equals(absolute)) {
                canceled.set(true);
                work.close();
            }
            return true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(canceled.get() && work.finished(), "The callback must close the active task");
            helper.assertTrue(!completed.get(), "Canceled work must not run its completion callback");
            helper.assertBlockPresent(Blocks.STONE, position);
        });
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    public void removedOwnerCannotCommitQueuedWork(GameTestHelper helper) {
        var player = caster(helper);
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Blocks.STONE);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(helper.absolutePos(pos)), 1.0);
        var completed = new AtomicBoolean();
        work.onComplete(() -> completed.set(true));
        player.discard();
        work.advance();
        helper.assertTrue(
                work.finished() && !completed.get(), "Removal must cancel work even if health remains positive");
        helper.assertBlockPresent(Blocks.STONE, pos);
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    public void capacityRejectionDoesNotSpendEnergy(GameTestHelper helper) {
        var player = caster(helper);
        player.setAttached(SorcererAttachments.PROFILE, SorcererProfile.practiceProfile());
        var reservations = new ArrayList<TerrainDestruction.Work>();
        try {
            while (true) {
                var reservation = reserveTerrain(helper, player);
                if (reservation == null) break;
                reservations.add(reservation);
            }
            var fighter = fighter(helper, player);
            fighter.prepare(Technique.PURPLE);
            helper.assertTrue(fighter.cast() == null, "An over-capacity cast must be rejected");
            helper.assertTrue(
                    fighter.energy().current() == CursedEnergy.CAPACITY, "Rejected work must not spend startup energy");
            helper.assertTrue(fighter.energy().reserved() == 0, "Rejected work must not reserve release energy");
        } finally {
            reservations.forEach(TerrainDestruction.Work::close);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:terrain")
    public void removingQualificationDuringPreparationCancelsPurple(GameTestHelper helper) {
        var player = caster(helper);
        player.setAttached(SorcererAttachments.PROFILE, SorcererProfile.practiceProfile());
        var fighter = fighter(helper, player);
        fighter.prepare(Technique.PURPLE);
        var work = requireNonNull(requireNonNull(fighter.cast()).terrain());
        player.setAttached(SorcererAttachments.PROFILE, new SorcererProfile(1, true, false, false, true, 96.0));
        fighter.tick();
        helper.assertTrue(
                fighter.cast() == null && work.finished(), "Release must recheck qualification and free its slot");
        helper.assertTrue(
                fighter.energy().reserved() == 0, "Revoked qualification must cancel the release reservation");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void removalBudgetAndQueuedCancellationAreBounded(GameTestHelper helper) {
        var player = caster(helper);
        var blocks = BlockPos.betweenClosedStream(new BlockPos(3, 2, 4), new BlockPos(15, 14, 16))
                .map(BlockPos::immutable)
                .toList();
        blocks.forEach(pos -> helper.setBlock(pos, Blocks.DIRT));
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(helper.absoluteVec(new Vec3(9.0, 8.0, 10.0)), 9.0);
        TerrainDestruction.tick();
        var removed =
                blocks.stream().filter(pos -> helper.getBlockState(pos).isAir()).count();
        helper.assertTrue(
                removed >= 0 && removed <= TerrainDestruction.WRITES_PER_TICK,
                "A tick must stay within the direct block-removal budget");
        helper.assertTrue(!work.finished(), "A large operation must wait across ticks");
        work.close();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    blocks.stream()
                                    .filter(pos -> helper.getBlockState(pos).isAir())
                                    .count()
                            == removed,
                    "Closed work must not keep excavating");
            helper.succeed();
        });
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void protectedBlockDoesNotCancelTheRestOfASphere(GameTestHelper helper) {
        var player = caster(helper);
        var center = new BlockPos(8, 6, 10);
        var stone = center.east(2);
        helper.setBlock(center, Blocks.CHEST);
        helper.setBlock(stone, Blocks.STONE);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(helper.absolutePos(center)), 4.0);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "Excavation must settle");
            helper.assertBlockPresent(Blocks.CHEST, center);
            helper.assertBlockPresent(Blocks.AIR, stone);
        });
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void callbackReplacementIsRecheckedAndShieldsTheRestOfItsColumn(GameTestHelper helper) {
        var player = caster(helper);
        var position = new BlockPos(8, 5, 6);
        var behind = position.south();
        var absolute = helper.absolutePos(position);
        helper.setBlock(position, Blocks.STONE);
        helper.setBlock(behind, Blocks.STONE);
        beforeBlockBreak(helper, player, (level, actor, pos, state, entity) -> {
            if (pos.equals(absolute)) level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            return true;
        });
        var work = requireNonNull(reserveTerrain(helper, player));
        var start = helper.absoluteVec(new Vec3(8.5, 5.5, 4.5));
        work.cuts(List.of(new SweptVolume(start, start.add(0.0, 0.0, 4.0), new Vec3(0.1, 0.1, 0.1))));
        var completions = new AtomicInteger();
        work.onComplete(completions::incrementAndGet);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished(), "The sweep must complete");
            helper.assertBlockPresent(Blocks.CHEST, position);
            helper.assertBlockPresent(Blocks.STONE, behind);
            helper.assertTrue(completions.get() == 1, "Completion must run exactly once");
        });
    }

    @GameTest(environment = "cursed-oath-test:terrain", structure = "cursed-oath-test:arena", maxTicks = 100)
    public void invalidOwnerCancelsTerrainAndDeferredDamage(GameTestHelper helper) {
        var player = caster(helper);
        var position = new BlockPos(8, 5, 6);
        helper.setBlock(position, Blocks.STONE);
        var work = requireNonNull(reserveTerrain(helper, player));
        work.sphere(Vec3.atCenterOf(helper.absolutePos(position)), 1.0);
        var completed = new AtomicBoolean();
        work.onComplete(() -> completed.set(true));
        player.setGameMode(GameType.SPECTATOR);
        helper.succeedWhen(() -> {
            helper.assertTrue(work.finished() && !completed.get(), "Invalid work must not commit deferred damage");
            helper.assertBlockPresent(Blocks.STONE, position);
        });
    }
}
