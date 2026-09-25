package io.github.romeoahmed.cursedoath.combat;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;

import io.github.romeoahmed.cursedoath.technique.Technique;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MeleePreparationGameTest {
    private static final int WINDOW = 60;

    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 80)
    public void preparationAllowsTimeToApproachAndIsConsumedOnce(GameTestHelper helper) {
        var fighter = fighter(helper, caster(helper));
        fighter.preparePulse();
        var spent = fighter.energy().current();
        helper.startSequence()
                .thenExecuteAfter(WINDOW, () -> {
                    helper.assertTrue(fighter.pulseReady(), "Preparation must remain available through three seconds");
                    fighter.preparePulse();
                    helper.assertTrue(
                            fighter.energy().current() == spent, "Repeated preparation must not charge again");
                    helper.assertTrue(fighter.consumePulse(), "The prepared strike must be accepted at the boundary");
                    helper.assertTrue(
                            !fighter.pulseReady() && !fighter.consumePulse(), "A strike consumes preparation once");
                })
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 80)
    public void repeatedPreparationCannotExtendTheWindow(GameTestHelper helper) {
        var fighter = fighter(helper, caster(helper));
        fighter.preparePulse();
        helper.startSequence()
                .thenExecuteAfter(WINDOW / 2, fighter::preparePulse)
                .thenExecuteAfter(WINDOW / 2 + 1, () -> {
                    helper.assertTrue(
                            !fighter.pulseReady() && !fighter.consumePulse(), "An expired strike must not qualify");
                    fighter.preparePulse();
                    helper.assertTrue(fighter.pulseReady(), "Fresh preparation must work after expiry");
                })
                .thenSucceed();
    }

    @GameTest(environment = "cursed-oath-test:combat")
    public void cancellationAndCastingClearPreparation(GameTestHelper helper) {
        var fighter = fighter(helper, caster(helper));
        fighter.preparePulse();
        fighter.cancel();
        helper.assertTrue(!fighter.consumePulse(), "Cancellation must clear preparation");
        fighter.preparePulse();
        fighter.prepare(Technique.DISMANTLE);
        helper.assertTrue(fighter.cast() != null && !fighter.pulseReady(), "Casting must clear prepared melee");
        fighter.preparePulse();
        helper.assertTrue(!fighter.pulseReady(), "Casting must prevent a new melee preparation");
        fighter.cancel();
        helper.succeed();
    }
}
