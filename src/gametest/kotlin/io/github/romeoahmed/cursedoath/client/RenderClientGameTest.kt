package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.resources.language.I18n
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Direct event fixtures isolate rendering from input and networking. */
class RenderClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.prepareScreenshots()
        context.worldBuilder().create().use { world ->
            world.server.runCommand("tp @a 0.5 -50 0.5 0 0")
            world.server.runCommand("gamemode creative @a")
            world.server.runCommand("execute as @a run cursedoath practice")
            world.connection.waitForClientboundPackets()
            world.server.runOnServer<RuntimeException> {
                val player = world.connection.serverPlayer
                player.abilities.flying = true
                player.onUpdateAbilities()
            }
            world.connection.waitForClientboundPackets()
            world.connection.waitForChunksRender()
            context.waitFor { CombatInput.snapshot?.enabled == true }
            captureLanguages(context)
            captureEffects(context)
        }
    }

    private fun captureLanguages(context: ClientGameTestContext) {
        val original = context.computeOnClient<String, RuntimeException> { it.languageManager.selected }
        try {
            for (language in listOf("en_us", "zh_cn", "ja_jp")) {
                changeLanguage(context, language)
                context.runOnClient<RuntimeException> {
                    check(I18n.get(Technique.BLUE.translationKey) != Technique.BLUE.translationKey)
                }
                context.capture("hud-$language")
            }
        } finally {
            changeLanguage(context, original)
        }
    }

    private fun changeLanguage(
        context: ClientGameTestContext,
        language: String,
    ) {
        val reload =
            context.computeOnClient<CompletableFuture<*>, RuntimeException> {
                if (it.languageManager.selected == language) {
                    CompletableFuture.completedFuture(Unit)
                } else {
                    it.languageManager.selected = language
                    it.options.languageCode = language
                    it.reloadResourcePacks()
                }
            }
        context.waitFor { reload.isDone }
        reload.join()
        context.waitFor { it.gui.overlay() == null }
    }

    private fun captureEffects(context: ClientGameTestContext) {
        val hidden = context.computeOnClient<Boolean, RuntimeException> { it.gui.hud.isHidden }
        try {
            if (!hidden) context.input.pressKey { it.keyToggleGui }
            for (technique in listOf(Technique.BLUE, Technique.RED, Technique.CLEAVE)) {
                verifyEffect(context, technique, 0f, false)
            }
            verifyEffect(context, Technique.CLEAVE, 0f, true)
            verifyEffect(context, Technique.CLEAVE, VERTICAL_PITCH, false)
        } finally {
            context.runOnClient<RuntimeException> {
                TechniqueVisuals.clear()
                if (it.gui.hud.isHidden != hidden) it.gui.hud.toggle()
            }
        }
    }

    private fun verifyEffect(
        context: ClientGameTestContext,
        technique: Technique,
        pitch: Float,
        reverse: Boolean,
    ) {
        context.input.lookAt(0f, pitch)
        context.waitTick()
        context.runOnClient<RuntimeException> { TechniqueVisuals.clear() }
        val name = "${technique.name.lowercase()}-${pitch.toInt()}-${if (reverse) "back" else "front"}"
        val baseline = context.capture("$name-before")
        context.runOnClient<RuntimeException> {
            val player = checkNotNull(it.player)
            val level = checkNotNull(it.level)
            val direction = player.lookAngle
            val distance = if (pitch == VERTICAL_PITCH) VERTICAL_DISTANCE else EFFECT_DISTANCE
            val destination = player.eyePosition.add(direction.scale(distance))
            // A successful contact may start inside the target; zero separation is not a miss.
            val origin =
                when {
                    reverse -> destination.add(direction)
                    technique == Technique.CLEAVE && pitch == 0f -> destination
                    else -> player.eyePosition
                }
            TechniqueVisuals.accept(
                TechniqueEvent(
                    level.dimension().identifier(),
                    UUID.randomUUID(),
                    player.id,
                    technique.wireId,
                    if (technique == Technique.CLEAVE) TechniqueEvent.RELEASE else TechniqueEvent.IMPACT,
                    level.gameTime - EFFECT_AGE,
                    origin,
                    destination,
                ),
            )
        }
        context.checkEffect(baseline, name, visible = true)
        context.waitTicks(EFFECT_EXPIRY)
        context.checkEffect(baseline, "$name-expired", visible = false)
    }

    private companion object {
        const val VERTICAL_PITCH = 90f
        const val VERTICAL_DISTANCE = 4.0
        const val EFFECT_DISTANCE = 12.0
        const val EFFECT_AGE = 3L
        const val EFFECT_EXPIRY = 65
    }
}
