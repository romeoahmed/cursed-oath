package io.github.romeoahmed.cursedoath.client.hud

import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.combat.CursedEnergy
import io.github.romeoahmed.cursedoath.network.CombatSnapshot
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object CombatHud {
    private const val MARGIN = 12
    private const val PADDING = 4
    private const val MAX_WIDTH = 220
    private const val BAR_HEIGHT = 4
    private val background = 0xB0121721.toInt()
    private val titleColor = 0xFF91A8BA.toInt()
    private val textColor = 0xFFFFFFFF.toInt()
    private val barBackground = 0xFF303E4F.toInt()
    private val reservedColor = 0xFF4786AC.toInt()
    private val availableColor = 0xFF88DAF0.toInt()
    private val statusColor = 0xFFD3DDE6.toInt()
    private val hintColor = 0xFFB4C4D1.toInt()

    fun initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CursedOath.id("combat")) { graphics, _ ->
            val state = CombatInput.snapshot
            if (state?.enabled == true) render(graphics, state)
        }
    }

    private fun render(
        graphics: GuiGraphicsExtractor,
        state: CombatSnapshot,
    ) {
        val font = Minecraft.getInstance().font
        val width = minOf(MAX_WIDTH, graphics.guiWidth() - MARGIN * 2).coerceAtLeast(1)
        val title = Component.translatable("hud.cursed-oath.practice")
        val name = Component.translatable(CombatInput.selected.translationKey)
        val nameY = MARGIN + font.wordWrapHeight(title, width) + PADDING
        val barY = nameY + font.wordWrapHeight(name, width) + PADDING
        val statusY = barY + BAR_HEIGHT + PADDING
        val status =
            when {
                state.preparing != 0 -> "preparing"
                state.recovery > 0 -> "recovering"
                state.infinity -> "infinity"
                else -> "ready"
            }
        val statusText = Component.translatable("hud.cursed-oath.$status")
        if (state.pulseReady) statusText.append("\n").append(Component.translatable("hud.cursed-oath.pulse_ready"))
        val hintY = statusY + font.wordWrapHeight(statusText, width) + PADDING
        val hint =
            Component.translatable(
                "hud.cursed-oath.keys",
                CombatInput.select.translatedKeyMessage,
                CombatInput.cast.translatedKeyMessage,
                CombatInput.cancel.translatedKeyMessage,
                CombatInput.pulse.translatedKeyMessage,
            )
        val bottom = hintY + font.wordWrapHeight(hint, width) + PADDING
        graphics.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN + width + PADDING, bottom, background)
        graphics.textWithWordWrap(font, title, MARGIN, MARGIN, width, titleColor)
        graphics.textWithWordWrap(font, name, MARGIN, nameY, width, textColor)
        graphics.energyBar(state, width, barY)
        graphics.textWithWordWrap(font, statusText, MARGIN, statusY, width, statusColor)
        graphics.textWithWordWrap(font, hint, MARGIN, hintY, width, hintColor)
    }

    private fun GuiGraphicsExtractor.energyBar(
        state: CombatSnapshot,
        width: Int,
        y: Int,
    ) {
        val filled = width * state.energy.coerceIn(0, CursedEnergy.CAPACITY) / CursedEnergy.CAPACITY
        val available = (state.energy - state.reserved).coerceIn(0, CursedEnergy.CAPACITY)
        fill(MARGIN, y, MARGIN + width, y + BAR_HEIGHT, barBackground)
        fill(MARGIN, y, MARGIN + filled, y + BAR_HEIGHT, reservedColor)
        fill(MARGIN, y, MARGIN + width * available / CursedEnergy.CAPACITY, y + BAR_HEIGHT, availableColor)
    }
}
