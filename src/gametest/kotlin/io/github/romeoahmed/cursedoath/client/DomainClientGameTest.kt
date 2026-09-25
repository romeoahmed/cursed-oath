package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.CloudStatus

class DomainClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.prepareScreenshots()
        // Pixel comparisons need fixed illumination and no drifting clouds.
        context.runOnClient<RuntimeException> { it.options.cloudStatus().set(CloudStatus.OFF) }
        context.worldBuilder().create().use { world ->
            world.server.runCommand("time set noon")
            world.server.runCommand("gamerule advance_time false")
            world.server.runCommand("weather clear")
            world.server.runCommand("gamerule advance_weather false")
            world.server.runCommand("gamemode creative @a")
            world.server.runCommand("tp @a 0.5 -50 0.5 0 0")
            world.server.runCommand("execute as @a run cursedoath practice")
            world.server.runOnServer<RuntimeException> {
                val player = world.connection.serverPlayer
                player.abilities.flying = true
                player.onUpdateAbilities()
            }
            world.connection.waitForClientboundPackets()
            world.connection.waitForChunksRender()
            context.waitFor { CombatInput.snapshot?.enabled == true }
            context.input.pressKey { it.keyToggleGui }
            verifyVoid(context, world)
            verifyShrine(context, world)
        }
    }

    private fun verifyVoid(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        context.waitTicks(SETTLE_TICKS)
        val baseline = context.capture("void-before")
        context.runOnClient<RuntimeException> { CombatInput.choose(Technique.UNLIMITED_VOID, cast = true) }
        context.waitFor { Domains.inLevel(checkNotNull(it.level)).any { domain -> domain.closed } }
        context.waitFor { CombatInput.snapshot?.burnout == 0 }
        context.waitTicks(REVEAL_TICKS)
        context.checkEffect(baseline, "void-interior", visible = true)
        context.capture("void-interior-full")
        world.server.runCommand("tp @a 0.5 -50 0.5 35 -10")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("void-interior-turned")
        world.server.runCommand("tp @a 0.5 -50 0.5 180 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("void-interior-back")
        world.server.runCommand("tp @a 5.5 -50 4.5 0 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("void-interior-moved")
        world.server.runCommand("tp @a 0.5 -50 -35 0 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("void-exterior")
        context.input.pressKey(CombatInput.cancel)
        context.waitFor { Domains.inLevel(checkNotNull(it.level)).isEmpty() }
        context.waitFor { (CombatInput.snapshot?.burnout ?: 0) > 0 }
        world.server.runCommand("tp @a 0.5 -50 0.5 0 0")
        world.connection.waitForClientboundPackets()
        world.connection.waitForChunksRender()
        context.waitTicks(SETTLE_TICKS)
        context.checkEffect(baseline, "void-collapsed", visible = false)
    }

    private fun verifyShrine(
        context: ClientGameTestContext,
        world: TestSingleplayerContext,
    ) {
        world.server.runCommand("tp @a 0.5 -60 0.5 0 0")
        world.server.runCommand("execute as @a run cursedoath practice")
        world.connection.waitForClientboundPackets()
        context.runOnClient<RuntimeException> { CombatInput.choose(Technique.MALEVOLENT_SHRINE, cast = true) }
        context.waitFor { Domains.inLevel(checkNotNull(it.level)).any { domain -> !domain.closed } }
        world.server.runCommand("summon minecraft:armor_stand 9.5 -60 -6 {Invulnerable:1b,NoGravity:1b,ShowArms:1b}")
        world.server.runCommand("tp @a 0.5 -53 -33 0 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(REVEAL_TICKS)
        context.capture("shrine-front")
        world.server.runCommand("tp @a -24 -51 -25 -58 4")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-quarter")
        world.server.runCommand("tp @a 0.5 -53 -26 0 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-mouth-detail")
        world.server.runCommand("tp @a -11 -53 -23 -43 -12")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-eaves-below")
        world.server.runCommand("tp @a -11 -55 -24 -38 14")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-ossuary-detail")
        world.server.runCommand("tp @a 0.5 -45 -25 0 11")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-front-gable")
        world.server.runCommand("tp @a 0.5 -45 4 180 11")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-rear-gable")
        world.server.runCommand("tp @a 0.5 -56 12 0 0")
        world.connection.waitForClientboundPackets()
        context.waitTicks(SETTLE_TICKS)
        repeat(SLASH_FRAMES) {
            context.capture("shrine-cuts-$it")
            context.waitTicks(2)
        }
        context.runOnClient<RuntimeException> { it.options.hideLightningFlash().set(true) }
        context.waitTicks(SETTLE_TICKS)
        context.capture("shrine-cuts-subdued")
        context.runOnClient<RuntimeException> { it.options.hideLightningFlash().set(false) }
        context.input.pressKey(CombatInput.cancel)
        context.waitFor { Domains.inLevel(checkNotNull(it.level)).isEmpty() }
        context.capture("shrine-collapsed")
    }

    private companion object {
        const val SLASH_FRAMES = 3
        const val REVEAL_TICKS = 30
        const val SETTLE_TICKS = 5
    }
}
