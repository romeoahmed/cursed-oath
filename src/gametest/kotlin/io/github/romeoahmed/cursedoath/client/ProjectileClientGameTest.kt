package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.CameraType
import net.minecraft.core.BlockPos
import java.util.UUID

/** Renders spawned projectiles through native tracking, independently of casting input. */
class ProjectileClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.prepareScreenshots()
        context.worldBuilder().create().use { world ->
            world.server.runCommand("gamemode creative @a")
            world.server.runCommand("tp @a 0.5 -45 0.5 0 0")
            world.server.runOnServer<RuntimeException> {
                world.connection.serverPlayer.abilities.flying = true
                world.connection.serverPlayer.onUpdateAbilities()
            }
            world.connection.waitForClientboundPackets()
            world.connection.waitForChunksRender()
            context.input.lookAt(0f, 0f)
            for (technique in listOf(Technique.BLUE, Technique.RED, Technique.DISMANTLE)) {
                flight(context, world, technique)
            }
            cleave(context, world)
            poses(context)
        }
    }

    private fun flight(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
        technique: Technique,
    ) {
        context.waitTicks(POSE_EXPIRY)
        world.server.runOnServer<RuntimeException> {
            TechniqueCombat.release(world.connection.serverPlayer, UUID.randomUUID(), technique)
        }
        context.waitFor { client ->
            client.level?.entitiesForRendering()?.any {
                it is TechniqueProjectile && it.technique == technique && it.z > FLIGHT_Z
            } == true
        }
        world.server.runCommand("tick freeze")
        context.capture("${technique.name.lowercase()}-tracked-flight")
        world.server.runCommand("tick unfreeze")
        context.waitFor { client ->
            client.level?.entitiesForRendering()?.none { it is TechniqueProjectile } == true
        }
    }

    private fun cleave(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        world.server.runCommand("fill -5 -49 3 5 -39 7 stone")
        world.connection.waitForChunksRender()
        val work =
            world.server.computeOnServer<TerrainDestruction.Work, RuntimeException> {
                val player = world.connection.serverPlayer
                val terrain = checkNotNull(TerrainDestruction.reserve(player))
                TechniqueCombat.release(player, UUID.randomUUID(), Technique.CLEAVE, terrain)
                terrain
            }
        world.server.waitFor {
            world.connection.serverLevel
                .getBlockState(CUT)
                .isAir
        }
        world.connection.waitForChunksRender()
        context.capture("cleave-contact-lattice")
        world.server.waitFor { work.finished }
        world.server.runCommand("tp @a 0.5 -45 -10.5 0 0")
        world.connection.waitForClientboundPackets()
        world.connection.waitForChunksRender()
        context.capture("cleave-terrain-after")
        context.waitTicks(DEBRIS_EXPIRY)
        context.capture("cleave-terrain-settled")
    }

    private fun poses(context: ClientGameTestContext) {
        val original = context.computeOnClient<CameraType, RuntimeException> { it.options.cameraType }
        try {
            context.runOnClient<RuntimeException> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            for (technique in listOf(
                Technique.BLUE,
                Technique.RED,
                Technique.PURPLE,
                Technique.DISMANTLE,
                Technique.CLEAVE,
            )) {
                context.runOnClient<RuntimeException> {
                    CastingAnimation.start(checkNotNull(it.player), technique, false, 0f)
                }
                context.waitTicks(POSE_TICKS)
                context.capture("${technique.name.lowercase()}-preparation-pose")
                context.runOnClient<RuntimeException> {
                    CastingAnimation.start(checkNotNull(it.player), technique, true, 0f)
                }
                context.waitTicks(RELEASE_TICKS)
                context.capture("${technique.name.lowercase()}-release-pose")
                context.waitTicks(POSE_EXPIRY)
            }
        } finally {
            context.runOnClient<RuntimeException> { it.options.setCameraType(original) }
        }
    }

    private companion object {
        val CUT = BlockPos(0, -44, 3)
        const val FLIGHT_Z = 9.0
        const val DEBRIS_EXPIRY = 40
        const val POSE_TICKS = 4
        const val RELEASE_TICKS = 2
        const val POSE_EXPIRY = 20
    }
}
