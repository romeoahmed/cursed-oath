package io.github.romeoahmed.cursedoath.client.gui;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.combat.CursedEnergy;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.CombatSnapshot;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class CombatHud {
    private static final int MARGIN = 10,
            PADDING = 5,
            MAX_WIDTH = 162,
            LINE_HEIGHT = 12,
            BAR_HEIGHT = 3,
            TICKS_PER_SECOND = 20;
    private static final int BACKGROUND = 0xC010131B,
            TEXT = 0xFFEAE8E2,
            MUTED = 0xFF9CAAB8,
            BAR_BACKGROUND = 0xFF303743,
            RESERVED = 0xFF526479,
            AVAILABLE = 0xFF9DDDDC,
            OPAQUE = 0xFF000000;

    private CombatHud() {}

    public static void initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CursedOath.id("combat"), (graphics, delta) -> {
            var state = CombatInput.snapshot();
            if (state != null
                    && state.enabled()
                    && !(Minecraft.getInstance().gui.screen() instanceof TechniqueWheelScreen)) render(graphics, state);
        });
    }

    private static void render(GuiGraphicsExtractor graphics, CombatSnapshot state) {
        var font = Minecraft.getInstance().font;
        int width = Math.max(1, Math.min(MAX_WIDTH, graphics.guiWidth() - MARGIN * 2));
        var preparing = Technique.fromWire(state.preparing());
        var technique = preparing == null ? CombatInput.selected() : preparing;
        var name = Component.translatable("wheel.cursed-oath." + technique.path());
        var hint = Component.translatable(
                "hud.cursed-oath.keys",
                CombatInput.WHEEL.getTranslatedKeyMessage(),
                CombatInput.CAST.getTranslatedKeyMessage());
        var status = statuses(state);
        int statusHeight = 0;
        for (var line : status) statusHeight += font.wordWrapHeight(line, width) + PADDING;
        int bottom = MARGIN + LINE_HEIGHT * 2 + BAR_HEIGHT + PADDING * 2 + statusHeight;
        graphics.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN + width + PADDING, bottom, BACKGROUND);
        graphics.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN - PADDING + 1, bottom, technique.color() | OPAQUE);
        graphics.text(font, name, MARGIN, MARGIN, TEXT);
        if (font.width(name) + PADDING + font.width(hint) <= width)
            graphics.text(font, hint, MARGIN + width - font.width(hint), MARGIN, MUTED);
        int available = Math.clamp(state.energy() - state.reserved(), 0, CursedEnergy.CAPACITY);
        graphics.text(
                font, Component.translatable("gui.cursed-oath.energy", available), MARGIN, MARGIN + LINE_HEIGHT, MUTED);
        int barY = MARGIN + LINE_HEIGHT * 2;
        graphics.fill(MARGIN, barY, MARGIN + width, barY + BAR_HEIGHT, BAR_BACKGROUND);
        graphics.fill(
                MARGIN,
                barY,
                MARGIN + width * Math.clamp(state.energy(), 0, CursedEnergy.CAPACITY) / CursedEnergy.CAPACITY,
                barY + BAR_HEIGHT,
                RESERVED);
        graphics.fill(MARGIN, barY, MARGIN + width * available / CursedEnergy.CAPACITY, barY + BAR_HEIGHT, AVAILABLE);
        int y = barY + BAR_HEIGHT + PADDING;
        for (var line : status) {
            graphics.textWithWordWrap(font, line, MARGIN, y, width, TEXT);
            y += font.wordWrapHeight(line, width) + PADDING;
        }
    }

    private static List<Component> statuses(CombatSnapshot state) {
        var status = new ArrayList<Component>();
        if (state.preparing() != 0)
            status.add(
                    Component.translatable("hud.cursed-oath.preparing", CombatInput.CANCEL.getTranslatedKeyMessage()));
        else if (state.recovery() > 0) status.add(Component.translatable("hud.cursed-oath.recovering"));
        if (state.infinity()) status.add(Component.translatable("hud.cursed-oath.infinity"));
        if (state.pulseReady()) status.add(Component.translatable("hud.cursed-oath.pulse_ready"));
        if (state.burnout() > 0)
            status.add(Component.translatable(
                    "hud.cursed-oath.burnout", (state.burnout() + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND));
        if (state.simpleDomain() > 0)
            status.add(Component.translatable("hud.cursed-oath.simple_domain", state.simpleDomain()));
        if (state.amplification()) status.add(Component.translatable("hud.cursed-oath.amplification"));
        var player = Minecraft.getInstance().player;
        if (player != null && Domains.isOverloaded(player))
            status.add(Component.translatable("hud.cursed-oath.overloaded"));
        return status;
    }
}
