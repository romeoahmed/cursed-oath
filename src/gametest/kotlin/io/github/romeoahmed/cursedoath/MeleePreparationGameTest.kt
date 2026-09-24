package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.combat.Fighter
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

class MeleePreparationGameTest {
    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 80)
    fun preparationAllowsTimeToApproachAndIsConsumedOnce(helper: GameTestHelper) {
        val fighter = Fighter(helper.caster())
        fighter.preparePulse()
        val spent = fighter.energy.current
        helper
            .startSequence()
            .thenExecuteAfter(WINDOW) {
                helper.assertTrue(fighter.pulseReady, "Preparation must remain available through three seconds")
                fighter.preparePulse()
                helper.assertTrue(fighter.energy.current == spent, "Repeated preparation must not charge again")
                helper.assertTrue(fighter.consumePulse(), "The prepared strike must be accepted at the boundary")
                helper.assertTrue(!fighter.pulseReady && !fighter.consumePulse(), "A strike consumes preparation once")
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:combat", maxTicks = 80)
    fun repeatedPreparationCannotExtendTheWindow(helper: GameTestHelper) {
        val fighter = Fighter(helper.caster())
        fighter.preparePulse()
        helper
            .startSequence()
            .thenExecuteAfter(WINDOW / 2) {
                fighter.preparePulse()
            }.thenExecuteAfter(WINDOW / 2 + 1) {
                helper.assertTrue(!fighter.pulseReady && !fighter.consumePulse(), "An expired strike must not qualify")
                fighter.preparePulse()
                helper.assertTrue(fighter.pulseReady, "Fresh preparation must work after expiry")
            }.thenSucceed()
    }

    @GameTest(environment = "cursed-oath-test:combat")
    fun cancellationAndCastingClearPreparation(helper: GameTestHelper) {
        val fighter = Fighter(helper.caster())
        fighter.preparePulse()
        fighter.cancel()
        helper.assertTrue(!fighter.consumePulse(), "Cancellation must clear preparation")
        fighter.preparePulse()
        fighter.prepare(Technique.DISMANTLE)
        helper.assertTrue(fighter.cast != null && !fighter.pulseReady, "Casting must clear prepared melee")
        fighter.preparePulse()
        helper.assertTrue(!fighter.pulseReady, "Casting must prevent a new melee preparation")
        fighter.cancel()
        helper.succeed()
    }

    private companion object {
        const val WINDOW = 60
    }
}
