package io.github.romeoahmed.cursedoath.client.gui

import com.mojang.blaze3d.platform.InputConstants
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Directional picking with native buttons for keyboard focus and narration. */
class TechniqueWheelScreen : Screen(Component.translatable("key.cursed-oath.wheel")) {
    private val choices = linkedMapOf<Button, Technique>()
    private var preview = CombatInput.selected
    private var pointerSelection = false
    private var radiusX = 0.0
    private var radiusY = 0.0
    private val centerX get() = width / 2
    private val centerY get() = height / 2 - CENTER_OFFSET

    override fun isPauseScreen() = false

    override fun init() {
        choices.clear()
        radiusX = minOf(RADIUS_X, (width - SLOT_WIDTH - MARGIN).toDouble() / 2)
        radiusY = minOf(RADIUS_Y, (height - VERTICAL_MARGIN).toDouble() / 2)
        for ((index, technique) in ring.withIndex()) {
            val angle = index * PI / 4 - PI / 2
            addChoice(
                technique,
                centerX + (cos(angle) * radiusX).roundToInt(),
                centerY + (sin(angle) * radiusY).roundToInt(),
            )
        }
        for ((index, technique) in utility.withIndex()) {
            addChoice(
                technique,
                centerX + (index - 1) * (SLOT_WIDTH + GAP),
                centerY + radiusY.toInt() + UTILITY_OFFSET,
            )
        }
    }

    override fun setInitialFocus() {
        choices.entries
            .firstOrNull { it.value == CombatInput.selected }
            ?.key
            ?.let { setInitialFocus(it) }
    }

    private fun addChoice(
        technique: Technique,
        x: Int,
        y: Int,
    ) {
        val button =
            object : Button(
                x - SLOT_WIDTH / 2,
                y - SLOT_HEIGHT / 2,
                SLOT_WIDTH,
                SLOT_HEIGHT,
                Component.translatable("wheel.cursed-oath.${technique.path}"),
                { select(technique, false) },
                { it.get() },
            ) {
                override fun extractContents(
                    graphics: GuiGraphicsExtractor,
                    mouseX: Int,
                    mouseY: Int,
                    partialTick: Float,
                ) {
                    val highlighted = technique == preview
                    val color = technique.color or OPAQUE
                    val background = if (highlighted) HIGHLIGHT else PANEL
                    graphics.fill(this.x, this.y, this.x + width, this.y + height, background)
                    graphics.fill(this.x, this.y, this.x + 2, this.y + height, color)
                    if (highlighted) graphics.outline(this.x, this.y, width, height, color)
                    if (technique == CombatInput.selected) {
                        graphics.fill(
                            this.x + width - MARKER_SIZE - GAP,
                            this.y + GAP,
                            this.x + width - GAP,
                            this.y + GAP + MARKER_SIZE,
                            TEXT,
                        )
                    }
                    val label = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                    extractDefaultLabel(label)
                }
            }
        choices[addRenderableWidget(button)] = technique
    }

    override fun extractBackground(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.fill(0, 0, width, height, BACKDROP)
        minecraft.gui.hud.extractDeferredSubtitles()
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        preview =
            if (pointerSelection) {
                choiceAt(mouseX.toDouble(), mouseY.toDouble()) ?: CombatInput.selected
            } else {
                choices.entries.firstOrNull { it.key.isFocused }?.value ?: CombatInput.selected
            }
        for ((index, technique) in ring.withIndex()) {
            val angle = index * PI / 4 - PI / 2
            val color = if (technique == preview) technique.color or OPAQUE else GUIDE
            for (step in SPOKE_START..SPOKE_END) {
                val t = step.toDouble() / SPOKE_STEPS
                val x = centerX + (cos(angle) * radiusX * t).roundToInt()
                val y = centerY + (sin(angle) * radiusY * t).roundToInt()
                graphics.fill(x, y, x + 1, y + 1, color)
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        TechniqueDetails.draw(graphics, preview, centerX, centerY, width - MARGIN * 2)
        val help = Component.translatable("gui.cursed-oath.technique_help", CombatInput.cast.translatedKeyMessage)
        val lines = font.split(help, width - MARGIN * 2)
        for ((i, line) in lines.withIndex()) {
            graphics.centeredText(font, line, centerX, height - GAP - (lines.size - i) * LINE_HEIGHT, MUTED)
        }
    }

    override fun mouseMoved(
        mouseX: Double,
        mouseY: Double,
    ) {
        pointerSelection = true
        preview = choiceAt(mouseX, mouseY) ?: CombatInput.selected
        super.mouseMoved(mouseX, mouseY)
    }

    private fun choiceAt(
        x: Double,
        y: Double,
    ): Technique? {
        if (radiusX <= 0 || radiusY <= 0) return null
        val button = choices.entries.firstOrNull { it.key.isMouseOver(x, y) }
        val dx = (x - centerX) / radiusX
        val dy = (y - centerY) / radiusY
        val distance = dx * dx + dy * dy
        val inPanel =
            kotlin.math.abs(x - centerX) <= PANEL_WIDTH / 2 &&
                kotlin.math.abs(y - centerY) <= PANEL_HEIGHT / 2
        return when {
            button != null -> {
                button.value
            }

            inPanel || distance !in INNER_RADIUS_SQUARED..OUTER_RADIUS_SQUARED -> {
                null
            }

            else -> {
                val index = ((atan2(dy, dx) + PI / 2) / (PI / 4)).roundToInt()
                ring[Math.floorMod(index, ring.size)]
            }
        }
    }

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            choiceAt(event.x(), event.y())?.let {
                select(it, event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (CombatInput.wheel.matches(event)) {
            onClose()
            return true
        }
        if (CombatInput.cast.matches(event)) {
            select(
                if (pointerSelection) preview else choices.entries.firstOrNull { it.key.isFocused }?.value ?: preview,
                true,
            )
            return true
        }
        pointerSelection = false
        return super.keyPressed(event)
    }

    private fun select(
        technique: Technique,
        cast: Boolean,
    ) {
        CombatInput.choose(technique, cast)
        onClose()
    }

    private companion object {
        val ring =
            listOf(
                Technique.BLUE,
                Technique.RED,
                Technique.PURPLE,
                Technique.UNLIMITED_VOID,
                Technique.MALEVOLENT_SHRINE,
                Technique.CLEAVE,
                Technique.DISMANTLE,
                Technique.INFINITY,
            )
        val utility = listOf(Technique.HEAL, Technique.SIMPLE_DOMAIN, Technique.AMPLIFICATION)
        const val SLOT_WIDTH = 70
        const val SLOT_HEIGHT = 24
        const val GAP = 6
        const val CENTER_OFFSET = 10
        const val RADIUS_X = 110.0
        const val RADIUS_Y = 66.0
        const val MARGIN = 12
        const val VERTICAL_MARGIN = 128
        const val UTILITY_OFFSET = 28
        const val LINE_HEIGHT = 11
        const val PANEL_WIDTH = 116
        const val PANEL_HEIGHT = 44
        const val INNER_RADIUS_SQUARED = 0.4
        const val OUTER_RADIUS_SQUARED = 1.5
        const val BACKDROP = 0x78080A12
        const val PANEL = 0xEA10141E.toInt()
        const val OPAQUE = 0xFF000000.toInt()
        const val TEXT = 0xFFF1EDE4.toInt()
        const val MUTED = 0xFFADBACB.toInt()
        const val HIGHLIGHT = 0xF0252D40.toInt()
        const val GUIDE = 0x806B7B91.toInt()
        const val MARKER_SIZE = 2
        const val SPOKE_START = 7
        const val SPOKE_END = 9
        const val SPOKE_STEPS = 12
    }
}
