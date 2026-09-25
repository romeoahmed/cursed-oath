package io.github.romeoahmed.cursedoath.client.gui

import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.network.CombatSnapshot
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object CombatHud {
    private const val MARGIN = 10
    private const val PADDING = 5
    private const val MAX_WIDTH = 162
    private const val LINE_HEIGHT = 12
    private const val BAR_HEIGHT = 3
    private const val TICKS_PER_SECOND = 20
    private val background = 0xC010131B.toInt()
    private val textColor = 0xFFEAE8E2.toInt()
    private val mutedColor = 0xFF9CAAB8.toInt()
    private val barBackground = 0xFF303743.toInt()
    private val reservedColor = 0xFF526479.toInt()
    private val availableColor = 0xFF9DDDDC.toInt()

    fun initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CursedOath.id("combat")) { graphics, _ ->
            val state = CombatInput.snapshot
            if (state?.enabled == true && Minecraft.getInstance().gui.screen() !is TechniqueWheelScreen) {
                render(graphics, state)
            }
        }
    }

    private fun render(
        graphics: GuiGraphicsExtractor,
        state: CombatSnapshot,
    ) {
        val font = Minecraft.getInstance().font
        val width = minOf(MAX_WIDTH, graphics.guiWidth() - MARGIN * 2).coerceAtLeast(1)
        val technique = Technique.fromWire(state.preparing) ?: CombatInput.selected
        val name = Component.translatable("wheel.cursed-oath.${technique.path}")
        val hint =
            Component.translatable(
                "hud.cursed-oath.keys",
                CombatInput.wheel.translatedKeyMessage,
                CombatInput.cast.translatedKeyMessage,
            )
        val status = statuses(state)
        val statusHeight = status.sumOf { font.wordWrapHeight(it, width) + PADDING }
        val bottom = MARGIN + LINE_HEIGHT * 2 + BAR_HEIGHT + PADDING * 2 + statusHeight
        graphics.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN + width + PADDING, bottom, background)
        graphics.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN - PADDING + 1, bottom, technique.color or OPAQUE)
        graphics.text(font, name, MARGIN, MARGIN, textColor)
        if (font.width(name) + PADDING + font.width(hint) <= width) {
            graphics.text(font, hint, MARGIN + width - font.width(hint), MARGIN, mutedColor)
        }
        val available = (state.energy - state.reserved).coerceIn(0, CursedEnergy.CAPACITY)
        val energy = Component.translatable("gui.cursed-oath.energy", available)
        graphics.text(font, energy, MARGIN, MARGIN + LINE_HEIGHT, mutedColor)
        val barY = MARGIN + LINE_HEIGHT * 2
        graphics.fill(MARGIN, barY, MARGIN + width, barY + BAR_HEIGHT, barBackground)
        graphics.fill(
            MARGIN,
            barY,
            MARGIN + width * state.energy.coerceIn(0, CursedEnergy.CAPACITY) / CursedEnergy.CAPACITY,
            barY + BAR_HEIGHT,
            reservedColor,
        )
        graphics.fill(
            MARGIN,
            barY,
            MARGIN + width * available / CursedEnergy.CAPACITY,
            barY + BAR_HEIGHT,
            availableColor,
        )
        var y = barY + BAR_HEIGHT + PADDING
        for (line in status) {
            graphics.textWithWordWrap(font, line, MARGIN, y, width, textColor)
            y += font.wordWrapHeight(line, width) + PADDING
        }
    }

    private fun statuses(state: CombatSnapshot): List<Component> =
        buildList {
            when {
                state.preparing != 0 -> {
                    add(
                        Component.translatable("hud.cursed-oath.preparing", CombatInput.cancel.translatedKeyMessage),
                    )
                }

                state.recovery > 0 -> {
                    add(Component.translatable("hud.cursed-oath.recovering"))
                }
            }
            if (state.infinity) add(Component.translatable("hud.cursed-oath.infinity"))
            if (state.pulseReady) add(Component.translatable("hud.cursed-oath.pulse_ready"))
            if (state.burnout >
                0
            ) {
                add(
                    Component.translatable(
                        "hud.cursed-oath.burnout",
                        (state.burnout + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND,
                    ),
                )
            }
            if (state.simpleDomain > 0) add(Component.translatable("hud.cursed-oath.simple_domain", state.simpleDomain))
            if (state.amplification) add(Component.translatable("hud.cursed-oath.amplification"))
            if (Minecraft.getInstance().player?.let(Domains::isOverloaded) ==
                true
            ) {
                add(Component.translatable("hud.cursed-oath.overloaded"))
            }
        }

    private const val OPAQUE = 0xFF000000.toInt()
}
