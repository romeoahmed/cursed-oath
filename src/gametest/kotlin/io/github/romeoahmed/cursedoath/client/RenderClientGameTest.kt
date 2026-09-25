package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.gui.components.Button
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
            world.server.runCommand("gamemode spectator @a")
            world.connection.waitForClientboundPackets()
            captureEffects(context)
            world.server.runCommand("gamemode creative @a")
            world.server.runOnServer<RuntimeException> {
                world.connection.serverPlayer.abilities.flying = true
                world.connection.serverPlayer.onUpdateAbilities()
            }
            world.connection.waitForClientboundPackets()
            captureFusionPose(context)
        }
    }

    private fun captureFusionPose(context: ClientGameTestContext) {
        val original = context.computeOnClient<CameraType, RuntimeException> { it.options.cameraType }
        try {
            context.input.lookAt(0f, 0f)
            context.runOnClient<RuntimeException> { it.options.cameraType = CameraType.THIRD_PERSON_FRONT }
            for ((release, ages) in listOf(false to FUSION_AGES, true to RELEASE_AGES)) {
                for (age in ages) {
                    context.runOnClient<RuntimeException> {
                        CastingAnimation.start(checkNotNull(it.player), Technique.PURPLE, release, age.toFloat())
                    }
                    context.waitTick()
                    context.capture("purple-pose-${if (release) "release" else "fusion"}-$age")
                }
            }
        } finally {
            context.runOnClient<RuntimeException> {
                CastingAnimation.stop(checkNotNull(it.player))
                it.options.cameraType = original
            }
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
                context.runOnClient<RuntimeException> {
                    CombatInput.choose(Technique.MALEVOLENT_SHRINE)
                    it.gui.setScreen(TechniqueWheelScreen())
                }
                verifyWheelLayout(context)
                context.capture("wheel-$language")
                context.input.resizeWindow(COMPACT_WIDTH, COMPACT_HEIGHT)
                context.waitTick()
                verifyWheelLayout(context)
                context.capture("wheel-$language-compact")
                context.prepareScreenshots()
                context.runOnClient<RuntimeException> {
                    it.gui.setScreen(null)
                    CombatInput.choose(Technique.BLUE)
                }
            }
        } finally {
            changeLanguage(context, original)
        }
    }

    private fun verifyWheelLayout(context: ClientGameTestContext) {
        context.runOnClient<RuntimeException> {
            val screen = it.gui.screen() as TechniqueWheelScreen
            val buttons = screen.children().filterIsInstance<Button>()
            check(buttons.size == Technique.entries.size) { "Every technique needs an accessible choice" }
            for (button in buttons) {
                check(button.x >= 0 && button.y >= 0 && button.right <= screen.width && button.bottom <= screen.height)
                check(
                    it.font.width(button.message) <= button.width - LABEL_PADDING,
                ) { "Short names must fit without scrolling" }
                for (other in buttons) {
                    if (other === button) continue
                    check(
                        button.right <= other.x || other.right <= button.x || button.bottom <= other.y ||
                            other.bottom <= button.y,
                    ) {
                        "Wheel choices must not overlap"
                    }
                }
            }
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
            for (technique in listOf(Technique.BLUE, Technique.RED, Technique.PURPLE)) {
                verifyEffect(context, Effect(technique, TechniqueEvent.PREPARE, technique.preparation / CHARGE_SAMPLE))
            }
            for (age in FUSION_AGES) {
                verifyEffect(context, Effect(Technique.PURPLE, TechniqueEvent.PREPARE, age), verifyExpiry = false)
            }
            for (technique in listOf(Technique.HEAL, Technique.RED, Technique.CLEAVE)) {
                verifyEffect(context, Effect(technique))
            }
            verifyEffect(context, Effect(Technique.CLEAVE, reverse = true))
            verifyEffect(context, Effect(Technique.CLEAVE, pitch = VERTICAL_PITCH))
            verifyEffect(context, Effect(Technique.CLEAVE, TechniqueEvent.BLACK_FLASH))
        } finally {
            context.runOnClient<RuntimeException> {
                TechniqueVisuals.clear()
                if (it.gui.hud.isHidden != hidden) it.gui.hud.toggle()
            }
        }
    }

    private data class Effect(
        val technique: Technique,
        val stage: Int = if (technique == Technique.RED) TechniqueEvent.IMPACT else TechniqueEvent.RELEASE,
        val age: Int = EFFECT_AGE,
        val pitch: Float = 0f,
        val reverse: Boolean = false,
    ) {
        val distance: Double get() =
            when {
                stage == TechniqueEvent.PREPARE && technique == Technique.PURPLE -> FUSION_DISTANCE
                stage == TechniqueEvent.PREPARE -> CHARGE_DISTANCE
                pitch == VERTICAL_PITCH -> VERTICAL_DISTANCE
                else -> EFFECT_DISTANCE
            }
    }

    private fun verifyEffect(
        context: ClientGameTestContext,
        effect: Effect,
        verifyExpiry: Boolean = true,
    ) {
        val technique = effect.technique
        val stage = effect.stage
        val age = effect.age
        val pitch = effect.pitch
        val reverse = effect.reverse
        context.input.lookAt(0f, pitch)
        context.waitTick()
        context.runOnClient<RuntimeException> { TechniqueVisuals.clear() }
        val label = if (stage == TechniqueEvent.BLACK_FLASH) "black-flash" else technique.name.lowercase()
        val name = "$label-${pitch.toInt()}-${if (reverse) "back" else "front"}-$stage-$age"
        val baseline = context.capture("$name-before")
        context.runOnClient<RuntimeException> {
            val player = checkNotNull(it.player)
            val level = checkNotNull(it.level)
            val direction = player.lookAngle
            val destination = player.eyePosition.add(direction.scale(effect.distance))
            // A successful contact may start inside the target; zero separation is not a miss.
            val origin =
                when {
                    reverse -> destination.add(direction)
                    technique == Technique.CLEAVE && stage != TechniqueEvent.BLACK_FLASH && pitch == 0f -> destination
                    else -> player.eyePosition
                }
            TechniqueVisuals.accept(
                TechniqueEvent(
                    level.dimension().identifier(),
                    UUID.randomUUID(),
                    if (stage == TechniqueEvent.PREPARE) -1 else player.id,
                    technique.wireId,
                    stage,
                    level.gameTime - age,
                    origin,
                    destination,
                ),
            )
        }
        context.checkEffect(baseline, name, visible = true)
        if (verifyExpiry) {
            val lifetime = if (stage == TechniqueEvent.PREPARE) technique.preparation else EFFECT_EXPIRY
            context.waitTicks((lifetime - age + 2).coerceAtLeast(1))
            context.checkEffect(baseline, "$name-expired", visible = false)
        }
    }

    private companion object {
        const val LABEL_PADDING = 4
        const val COMPACT_WIDTH = 640
        const val COMPACT_HEIGHT = 480
        const val VERTICAL_PITCH = 90f
        const val VERTICAL_DISTANCE = 4.0
        const val EFFECT_DISTANCE = 12.0
        const val EFFECT_AGE = 3
        const val CHARGE_DISTANCE = 2.0
        const val FUSION_DISTANCE = 8.0
        const val CHARGE_SAMPLE = 3
        val FUSION_AGES = listOf(32, 36, 49)
        val RELEASE_AGES = listOf(0, 2, 7)
        const val EFFECT_EXPIRY = 20
    }
}
