package io.github.romeoahmed.cursedoath.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.UUID

class CombatClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.prepareScreenshots()
        context.worldBuilder().create().use { world ->
            world.server.runCommand("tp @a 0.5 -60 0.5 0 0")
            world.server.runCommand("execute as @a run cursedoath practice")
            world.connection.waitForClientboundPackets()
            world.connection.waitForChunksRender()
            context.waitFor { CombatInput.snapshot?.enabled == true }
            verifyWheel(context, world)
            verifyMeleePreparation(context, world)
            verifyEmptySwing(context, world)
            verifyInfinity(context, world)
            context.input.lookAt(0f, 0f)
            context.input.pressKey(CombatInput.select)
            context.runOnClient<RuntimeException> { check(CombatInput.selected == Technique.RED) }
            val target = world.spawnTarget()
            world.connection.waitForClientboundEntityUpdates(EntityTypes.VILLAGER)
            verifyCancellation(context, world, target)
            verifyRelease(context, world, target)
        }
        context.runOnClient<RuntimeException> {
            check(CombatInput.snapshot == null) { "Disconnect must clear the HUD state" }
            check(CombatInput.selected == Technique.BLUE) { "Disconnect must reset technique selection" }
        }
    }

    private fun verifyWheel(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        context.input.pressKey(CombatInput.select)
        context.runOnClient<RuntimeException> { check(CombatInput.selected == Technique.RED) }
        context.input.pressKey(CombatInput.wheel)
        context.waitFor { it.gui.screen() is TechniqueWheelScreen }
        context.capture("technique-wheel")
        context.runOnClient<RuntimeException> { check(!checkNotNull(it.gui.screen()).isPauseScreen()) }
        // Exercise the dead zone through native input, without calling the screen's picker.
        val center =
            context.computeOnClient<Pair<Double, Double>, RuntimeException> {
                it.window.screenWidth / 2.0 to it.window.screenHeight / 2.0
            }
        context.input.setCursorPos(center.first, center.second)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
        world.connection.waitForServerboundPackets()
        world.connection.waitForClientboundPackets()
        context.runOnClient<RuntimeException> {
            check(it.gui.screen() is TechniqueWheelScreen)
            check(CombatInput.snapshot?.preparing == 0)
        }
        pointAtBlue(context)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE)
        context.runOnClient<RuntimeException> { check(it.gui.screen() is TechniqueWheelScreen) }
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitFor { it.gui.screen() == null && CombatInput.selected == Technique.BLUE }
        world.connection.waitForServerboundPackets()
        world.connection.waitForClientboundPackets()
        context.runOnClient<RuntimeException> {
            val state = checkNotNull(CombatInput.snapshot)
            check(state.preparing == 0 && state.energy == CursedEnergy.CAPACITY) { "Left click must only select" }
        }
        context.input.pressKey(CombatInput.wheel)
        context.waitFor { it.gui.screen() is TechniqueWheelScreen }
        context.input.pressKey(InputConstants.KEY_TAB)
        context.input.pressKey(InputConstants.KEY_RETURN)
        context.waitFor { it.gui.screen() == null && CombatInput.selected == Technique.RED }
        context.input.pressKey(CombatInput.wheel)
        context.waitFor { it.gui.screen() is TechniqueWheelScreen }
        pointAtBlue(context)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
        context.waitFor { it.gui.screen() == null && CombatInput.snapshot?.preparing == Technique.BLUE.wireId }
        context.input.pressKey(CombatInput.cancel)
        context.waitFor { CombatInput.snapshot?.preparing == 0 }
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
    }

    private fun pointAtBlue(context: ClientGameTestContext) {
        val (x, y) =
            context.computeOnClient<Pair<Double, Double>, RuntimeException> {
                val wheel = it.gui.screen() as TechniqueWheelScreen
                val window = it.window
                val blue =
                    wheel.children().filterIsInstance<Button>().single { button ->
                        button.message == Component.translatable("wheel.cursed-oath.${Technique.BLUE.path}")
                    }
                (blue.x + blue.width / 2.0) * window.screenWidth / window.guiScaledWidth to
                    (blue.y + blue.height / 2.0) * window.screenHeight / window.guiScaledHeight
            }
        context.input.setCursorPos(x, y)
    }

    private fun verifyMeleePreparation(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        context.input.pressKey(CombatInput.pulse)
        world.connection.waitForServerboundPackets()
        context.waitFor { CombatInput.snapshot?.pulseReady == true }
        context.capture("melee-prepared")
        context.waitTicks(MELEE_APPROACH_TICKS)
        context.runOnClient<RuntimeException> {
            check(CombatInput.snapshot?.pulseReady == true) { "Preparation must allow time to approach" }
        }
        context.input.pressKey(CombatInput.cancel)
        context.waitFor { CombatInput.snapshot?.pulseReady == false }
        context.capture("melee-cancelled")
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
    }

    private fun verifyEmptySwing(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        context.input.lookAt(0f, SKY_PITCH)
        context.input.pressKey(CombatInput.pulse)
        context.waitFor { CombatInput.snapshot?.pulseReady == true }
        context.input.pressKey { it.keyAttack }
        world.connection.waitForServerboundPackets()
        context.waitFor { CombatInput.snapshot?.pulseReady == false }
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
    }

    private fun verifyInfinity(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        repeat(Technique.entries.size) {
            if (context.computeOnClient<Boolean, RuntimeException> { CombatInput.selected != Technique.INFINITY }) {
                context.input.pressKey(CombatInput.select)
            }
        }
        context.input.pressKey(CombatInput.cast)
        context.waitFor { CombatInput.snapshot?.infinity == true }
        world.server.runOnServer<RuntimeException> {
            val player = world.connection.serverPlayer
            val level = world.connection.serverLevel
            val attacker = checkNotNull(EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND))
            val before = player.health
            player.hurtServer(level, level.damageSources().mobAttack(attacker), MELEE_DAMAGE.toFloat())
            check(player.health == before) { "Active Infinity must reject native contact damage" }
            player.hurtServer(level, level.damageSources().generic(), MELEE_DAMAGE.toFloat())
            check(player.health < before) { "Infinity must not become universal damage immunity" }
            player.heal(player.maxHealth)
        }
        context.input.pressKey(CombatInput.cancel)
        context.waitFor { CombatInput.snapshot?.infinity == false }
        context.input.pressKey(CombatInput.select)
        context.runOnClient<RuntimeException> { check(CombatInput.selected == Technique.BLUE) }
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
    }

    private fun verifyCancellation(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
        target: UUID,
    ) {
        context.input.pressKey(CombatInput.cast)
        world.connection.waitForServerboundPackets()
        context.waitFor { CombatInput.snapshot?.preparing == Technique.RED.wireId }
        context.input.pressKey(CombatInput.cancel)
        world.connection.waitForServerboundPackets()
        context.waitFor { CombatInput.snapshot?.preparing == 0 }
        context.runOnClient<RuntimeException> {
            val state = checkNotNull(CombatInput.snapshot)
            check(state.reserved == 0) { "Cancellation must release the reservation" }
            check(state.energy == CursedEnergy.CAPACITY - Technique.RED.cost.startup)
            check(state.recovery > 0) { "Cancellation must retain recovery" }
        }
        context.waitTicks(Technique.RED.preparation)
        world.server.runOnServer<RuntimeException> {
            val entity = world.target(target)
            check(entity.health == entity.maxHealth) { "A cancelled cast must not deal delayed damage" }
        }
        context.waitFor { CombatInput.snapshot?.recovery == 0 }
    }

    private fun verifyRelease(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
        target: UUID,
    ) {
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
        context.waitFor { CombatInput.snapshot?.energy == CursedEnergy.CAPACITY }
        context.input.pressKey(CombatInput.cast)
        world.connection.waitForServerboundPackets()
        context.waitFor { CombatInput.snapshot?.preparing == Technique.RED.wireId }
        context.capture("red-preparation")
        world.server.waitFor { world.target(target).health < world.target(target).maxHealth }
        world.connection.waitForClientboundPackets()
        context.waitFor { CombatInput.snapshot?.preparing == 0 }
        context.runOnClient<RuntimeException> {
            val state = checkNotNull(CombatInput.snapshot)
            check(state.reserved == 0)
            val expected = CursedEnergy.CAPACITY - Technique.RED.cost.startup - Technique.RED.cost.release
            check(state.energy == expected) { "Red must leave $expected energy, got ${state.energy}" }
        }
        context.capture("red-impact")
        world.server.waitFor { world.target(target).z > TARGET_Z + 1 }
        world.connection.waitForClientboundEntityUpdates(EntityTypes.VILLAGER)
        val entityId = world.server.computeOnServer<Int, RuntimeException> { world.target(target).id }
        context.waitFor { (it.level?.getEntity(entityId)?.z ?: TARGET_Z) > TARGET_Z + 1 }
        context.capture("red-displacement")
    }

    private fun TestSingleplayerContext.spawnTarget(): UUID =
        server.computeOnServer<UUID, RuntimeException> {
            val level = connection.serverLevel
            val target = checkNotNull(EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND))
            target.setPos(TARGET_X, GROUND_Y, TARGET_Z)
            // NoAI disables native travel as well as decisions; zero walking speed preserves physics.
            checkNotNull(target.getAttribute(Attributes.MOVEMENT_SPEED)).baseValue = 0.0
            checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = TARGET_HEALTH
            target.health = TARGET_HEALTH.toFloat()
            level.addFreshEntity(target)
            target.uuid
        }

    private fun TestSingleplayerContext.target(id: UUID): LivingEntity =
        checkNotNull(connection.serverLevel.getEntity(id) as? LivingEntity)

    private companion object {
        const val SKY_PITCH = -90f
        const val MELEE_DAMAGE = 4.0
        const val MELEE_APPROACH_TICKS = 20
        const val TARGET_HEALTH = 500.0
        const val TARGET_X = 0.5
        const val GROUND_Y = -60.0
        const val TARGET_Z = 5.5
    }
}
