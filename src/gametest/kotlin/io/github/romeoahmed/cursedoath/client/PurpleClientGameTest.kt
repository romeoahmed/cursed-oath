package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.UUID

class PurpleClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.prepareScreenshots()
        context.worldBuilder().create().use { world ->
            setup(context, world)
            val target = world.spawnTarget()
            context.capture("purple-arena-before")
            select(context, Technique.PURPLE)
            context.input.pressKey(CombatInput.cast)
            world.connection.waitForServerboundPackets()
            context.waitFor { CombatInput.snapshot?.preparing == Technique.PURPLE.wireId }
            context.waitTicks(SEPARATION_TICKS)
            world.server.runCommand("tick freeze")
            context.capture("purple-separated-blue-red")
            world.server.runCommand("tick unfreeze")
            context.waitTicks(CHARGE_TICKS - SEPARATION_TICKS)
            world.server.runCommand("tick freeze")
            context.capture("purple-fusion")
            world.server.runCommand("tick unfreeze")
            context.waitFor { client ->
                client.level?.entitiesForRendering()?.any { wave ->
                    wave.type == TechniqueProjectiles.WAVE && wave.z > FLIGHT_Z
                } == true
            }
            world.server.runCommand("tick freeze")
            world.connection.waitForClientboundEntityUpdates(TechniqueProjectiles.WAVE)
            context.capture("purple-flight")
            world.server.runCommand("tick unfreeze")
            world.server.waitFor { world.target(target).health < TARGET_HEALTH }
            world.server.waitFor {
                world.connection.serverLevel
                    .getBlockState(WALL_CENTER)
                    .isAir
            }
            world.server.runCommand("tick freeze")
            world.connection.waitForClientboundPackets()
            world.connection.waitForChunksRender()
            verifyImpact(context, world, target)
            world.server.runCommand("tick unfreeze")
            context.waitFor { client ->
                client.level?.entitiesForRendering()?.any { wave ->
                    wave.type == TechniqueProjectiles.WAVE && wave.z > AFTER_WALL_Z
                } == true
            }
            world.server.runCommand("tick freeze")
            world.connection.waitForChunksRender()
            context.capture("purple-breached-wall")
            world.server.runCommand("tick unfreeze")
            awaitWaveRemoval(context, world)
            context.waitTicks(DEBRIS_EXPIRY)
            context.capture("purple-tunnel-settled")
            verifyBlue(context, world)
        }
    }

    private fun awaitWaveRemoval(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        world.server.waitFor {
            world.connection.serverLevel
                .getEntities(
                    TechniqueProjectiles.WAVE,
                ) { true }
                .isEmpty()
        }
        world.connection.waitForClientboundPackets()
        context.waitFor {
            it.level?.entitiesForRendering()?.none { entity ->
                entity.type ==
                    TechniqueProjectiles.WAVE
            } ==
                true
        }
    }

    private fun verifyImpact(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
        target: UUID,
    ) {
        world.server.runOnServer<RuntimeException> {
            check(
                world.connection.serverLevel
                    .getBlockState(WALL_CENTER)
                    .isAir,
            ) { "Queued Purple excavation must open the wall" }
            check(
                world.target(target).health == TARGET_HEALTH - TechniqueTuning.PURPLE_DAMAGE,
            ) { "Purple must deal one committed hit" }
        }
        context.runOnClient<RuntimeException> {
            val state = checkNotNull(CombatInput.snapshot)
            check(state.reserved == 0)
            val spent = Technique.PURPLE.cost.startup + Technique.PURPLE.cost.release
            check(state.energy == CursedEnergy.CAPACITY - spent)
        }
    }

    private fun setup(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        world.server.runCommand("tp @a 0.5 -50 0.5 0 0")
        world.server.runCommand("gamemode creative @a")
        world.server.runCommand("execute as @a run cursedoath practice")
        world.server.runCommand("fill -8 -56 9 8 -41 15 stone")
        world.server.runOnServer<RuntimeException> {
            world.connection.serverPlayer.abilities.flying = true
            world.connection.serverPlayer.onUpdateAbilities()
        }
        world.connection.waitForClientboundPackets()
        world.connection.waitForChunksRender()
        context.waitFor { CombatInput.snapshot?.enabled == true }
        context.input.lookAt(0f, 0f)
    }

    private fun verifyBlue(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        world.server.runCommand("execute as @a run cursedoath practice")
        world.server.runCommand("tp @a 0.5 -50 12.5 0 0")
        val id = world.spawnTarget()
        world.connection.waitForClientboundPackets()
        select(context, Technique.BLUE)
        context.input.pressKey(CombatInput.cast)
        world.connection.waitForServerboundPackets()
        world.server.waitFor { world.target(id).health < TARGET_HEALTH }
        world.server.waitFor { world.target(id).z < TARGET_Z - MOVEMENT_THRESHOLD }
        world.server.runCommand("tick freeze")
        world.server.runCommand("tp @a 0.5 -45 6.5 facing 0.5 -49 13.5")
        world.connection.waitForClientboundPackets()
        world.connection.waitForChunksRender()
        context.capture("blue-pull-and-compression")
        world.server.runCommand("tick unfreeze")
    }

    private fun select(
        context: ClientGameTestContext,
        technique: Technique,
    ) {
        repeat(Technique.entries.size) {
            if (context.computeOnClient<Boolean, RuntimeException> { CombatInput.selected == technique }) return
            context.input.pressKey(CombatInput.select)
        }
        error("Technique selection did not reach $technique")
    }

    private fun TestSingleplayerContext.spawnTarget(): UUID =
        server.computeOnServer<UUID, RuntimeException> {
            val target = checkNotNull(EntityTypes.VILLAGER.create(connection.serverLevel, EntitySpawnReason.COMMAND))
            target.setPos(TARGET_X, TARGET_Y, TARGET_Z)
            target.setNoGravity(true)
            checkNotNull(target.getAttribute(Attributes.MOVEMENT_SPEED)).baseValue = 0.0
            checkNotNull(target.getAttribute(Attributes.MAX_HEALTH)).baseValue = TARGET_HEALTH.toDouble()
            target.health = TARGET_HEALTH
            connection.serverLevel.addFreshEntity(target)
            target.uuid
        }

    private fun TestSingleplayerContext.target(id: UUID): LivingEntity =
        checkNotNull(connection.serverLevel.getEntity(id) as? LivingEntity)

    private companion object {
        val WALL_CENTER = BlockPos(0, -49, 10)
        const val DEBRIS_EXPIRY = 40
        const val SEPARATION_TICKS = 10
        const val CHARGE_TICKS = 38
        const val AFTER_WALL_Z = 30.0
        const val FLIGHT_Z = 9.0
        const val TARGET_X = 0.5
        const val TARGET_Y = -50.0
        const val TARGET_Z = 17.5
        const val TARGET_HEALTH = 500f
        const val MOVEMENT_THRESHOLD = 0.2
    }
}
