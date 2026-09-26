package io.github.romeoahmed.cursedoath.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Directional picking with native buttons for keyboard focus and narration.
public final class TechniqueWheelScreen extends Screen {
    private static final List<Technique> RING = List.of(
            Technique.BLUE,
            Technique.RED,
            Technique.PURPLE,
            Technique.UNLIMITED_VOID,
            Technique.MALEVOLENT_SHRINE,
            Technique.CLEAVE,
            Technique.DISMANTLE,
            Technique.INFINITY);
    private static final List<Technique> UTILITY =
            List.of(Technique.HEAL, Technique.SIMPLE_DOMAIN, Technique.AMPLIFICATION);
    private static final int SLOT_WIDTH = 70,
            SLOT_HEIGHT = 24,
            GAP = 6,
            CENTER_OFFSET = 10,
            MARGIN = 12,
            VERTICAL_MARGIN = 128,
            UTILITY_OFFSET = 28,
            LINE_HEIGHT = 11,
            PANEL_WIDTH = 116,
            PANEL_HEIGHT = 44;
    private static final double RADIUS_X = 110, RADIUS_Y = 66, INNER_RADIUS_SQUARED = 0.4, OUTER_RADIUS_SQUARED = 1.5;
    private static final int BACKDROP = 0x78080A12,
            PANEL = 0xEA10141E,
            OPAQUE = 0xFF000000,
            TEXT = 0xFFF1EDE4,
            MUTED = 0xFFADBACB,
            HIGHLIGHT = 0xF0252D40,
            GUIDE = 0x806B7B91,
            MARKER_SIZE = 2,
            SPOKE_START = 7,
            SPOKE_END = 9,
            SPOKE_STEPS = 12;
    private final Map<Button, Technique> choices = new LinkedHashMap<>();
    private Technique preview = CombatInput.selected();
    private boolean pointerSelection;
    private double radiusX, radiusY;

    public TechniqueWheelScreen() {
        super(Component.translatable("key.cursed-oath.wheel"));
    }

    private int centerX() {
        return width / 2;
    }

    private int centerY() {
        return height / 2 - CENTER_OFFSET;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        choices.clear();
        radiusX = Math.min(RADIUS_X, (width - SLOT_WIDTH - MARGIN) / 2.0);
        radiusY = Math.min(RADIUS_Y, (height - VERTICAL_MARGIN) / 2.0);
        for (int index = 0; index < RING.size(); index++) {
            double angle = index * Math.PI / 4 - Math.PI / 2;
            addChoice(
                    RING.get(index),
                    centerX() + (int) Math.round(Math.cos(angle) * radiusX),
                    centerY() + (int) Math.round(Math.sin(angle) * radiusY));
        }
        for (int index = 0; index < UTILITY.size(); index++)
            addChoice(
                    UTILITY.get(index),
                    centerX() + (index - 1) * (SLOT_WIDTH + GAP),
                    centerY() + (int) radiusY + UTILITY_OFFSET);
    }

    @Override
    protected void setInitialFocus() {
        for (var choice : choices.entrySet())
            if (choice.getValue() == CombatInput.selected()) {
                setInitialFocus(choice.getKey());
                return;
            }
    }

    private void addChoice(Technique technique, int x, int y) {
        var button =
                new Button(
                        x - SLOT_WIDTH / 2,
                        y - SLOT_HEIGHT / 2,
                        SLOT_WIDTH,
                        SLOT_HEIGHT,
                        Component.translatable("wheel.cursed-oath." + technique.path()),
                        ignored -> select(technique, false),
                        Supplier::get) {
                    @Override
                    public void extractContents(
                            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                        boolean highlighted = technique == preview;
                        int color = technique.color() | OPAQUE;
                        graphics.fill(
                                getX(),
                                getY(),
                                getX() + getWidth(),
                                getY() + getHeight(),
                                highlighted ? HIGHLIGHT : PANEL);
                        graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), color);
                        if (highlighted) graphics.outline(getX(), getY(), getWidth(), getHeight(), color);
                        if (technique == CombatInput.selected())
                            graphics.fill(
                                    getX() + getWidth() - MARKER_SIZE - GAP,
                                    getY() + GAP,
                                    getX() + getWidth() - GAP,
                                    getY() + GAP + MARKER_SIZE,
                                    TEXT);
                        extractDefaultLabel(
                                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
                    }
                };
        choices.put(addRenderableWidget(button), technique);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    private Technique focusedChoice() {
        for (var choice : choices.entrySet()) if (choice.getKey().isFocused()) return choice.getValue();
        return CombatInput.selected();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var pointed = pointerSelection ? choiceAt(mouseX, mouseY) : null;
        preview = pointerSelection ? pointed == null ? CombatInput.selected() : pointed : focusedChoice();
        for (int index = 0; index < RING.size(); index++) {
            var technique = RING.get(index);
            double angle = index * Math.PI / 4 - Math.PI / 2;
            int color = technique == preview ? technique.color() | OPAQUE : GUIDE;
            for (int step = SPOKE_START; step <= SPOKE_END; step++) {
                double t = (double) step / SPOKE_STEPS;
                int x = centerX() + (int) Math.round(Math.cos(angle) * radiusX * t),
                        y = centerY() + (int) Math.round(Math.sin(angle) * radiusY * t);
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        TechniqueDetails.draw(graphics, preview, centerX(), centerY(), width - MARGIN * 2);
        var help = Component.translatable("gui.cursed-oath.technique_help", CombatInput.CAST.getTranslatedKeyMessage());
        var lines = font.split(help, width - MARGIN * 2);
        for (int i = 0; i < lines.size(); i++)
            graphics.centeredText(
                    font, lines.get(i), centerX(), height - GAP - (lines.size() - i) * LINE_HEIGHT, MUTED);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        pointerSelection = true;
        var choice = choiceAt(mouseX, mouseY);
        preview = choice == null ? CombatInput.selected() : choice;
        super.mouseMoved(mouseX, mouseY);
    }

    private @Nullable Technique choiceAt(double x, double y) {
        if (radiusX <= 0 || radiusY <= 0) return null;
        for (var choice : choices.entrySet()) if (choice.getKey().isMouseOver(x, y)) return choice.getValue();
        double dx = (x - centerX()) / radiusX, dy = (y - centerY()) / radiusY, distance = dx * dx + dy * dy;
        boolean inPanel = Math.abs(x - centerX()) <= PANEL_WIDTH / 2 && Math.abs(y - centerY()) <= PANEL_HEIGHT / 2;
        if (inPanel || distance < INNER_RADIUS_SQUARED || distance > OUTER_RADIUS_SQUARED) return null;
        int index = (int) Math.round((Math.atan2(dy, dx) + Math.PI / 2) / (Math.PI / 4));
        return RING.get(Math.floorMod(index, RING.size()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            var choice = choiceAt(event.x(), event.y());
            if (choice != null) {
                select(choice, event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CombatInput.WHEEL.matches(event)) {
            onClose();
            return true;
        }
        if (CombatInput.CAST.matches(event)) {
            select(pointerSelection ? preview : focusedChoice(), true);
            return true;
        }
        pointerSelection = false;
        return super.keyPressed(event);
    }

    private void select(Technique technique, boolean cast) {
        CombatInput.choose(technique, cast);
        onClose();
    }
}
