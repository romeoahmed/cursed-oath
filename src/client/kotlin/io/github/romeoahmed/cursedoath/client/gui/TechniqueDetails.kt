package io.github.romeoahmed.cursedoath.client.gui

import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.network.CombatSnapshot
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Shows synchronized facts; the server remains responsible for authorizing casts. */
internal object TechniqueDetails {
    fun draw(
        graphics: GuiGraphicsExtractor,
        technique: Technique,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val font = Minecraft.getInstance().font
        for ((index, line) in font.split(Component.translatable(technique.translationKey), width).withIndex()) {
            graphics.centeredText(font, line, x, TITLE_Y + index * LINE_HEIGHT, TEXT)
        }
        val state = CombatInput.snapshot
        val energy = state?.let { (it.energy - it.reserved).coerceAtLeast(0) }
        graphics.centeredText(
            font,
            Component.translatable("gui.cursed-oath.energy", energy ?: "—"),
            x,
            y - LINE_HEIGHT,
            TEXT,
        )
        val active = state != null && active(technique, state)
        val cost = technique.cost.startup + technique.cost.release
        val detail =
            when {
                active -> {
                    Component.translatable("gui.cursed-oath.deactivate")
                }

                technique == Technique.INFINITY || technique == Technique.AMPLIFICATION -> {
                    Component.translatable(
                        "gui.cursed-oath.upkeep",
                    )
                }

                else -> {
                    Component.translatable("gui.cursed-oath.cost", cost)
                }
            }
        graphics.centeredText(font, detail, x, y + 1, TEXT)
        warning(technique, state, active)?.let {
            graphics.centeredText(font, Component.translatable(it), x, y + LINE_HEIGHT + 2, WARNING)
        }
    }

    private fun active(
        technique: Technique,
        state: CombatSnapshot,
    ) = when (technique) {
        Technique.INFINITY -> state.infinity
        Technique.AMPLIFICATION -> state.amplification
        Technique.SIMPLE_DOMAIN -> state.simpleDomain > 0
        else -> false
    }

    private fun warning(
        technique: Technique,
        state: CombatSnapshot?,
        active: Boolean,
    ): String? {
        if (Minecraft.getInstance().player?.let(Domains::isOverloaded) == true) return "hud.cursed-oath.overloaded"
        if (state == null || active) return null
        val cost = technique.cost.startup + technique.cost.release
        return when {
            state.energy - state.reserved < cost -> "gui.cursed-oath.insufficient"
            state.amplification && technique.innate -> "gui.cursed-oath.amplification"
            state.burnout > 0 && technique.innate -> "gui.cursed-oath.burnout"
            else -> null
        }
    }

    private const val TITLE_Y = 12
    private const val LINE_HEIGHT = 11
    private const val TEXT = 0xFFF1EDE4.toInt()
    private const val WARNING = 0xFFFFAD78.toInt()
}
