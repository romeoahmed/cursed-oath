package io.github.romeoahmed.cursedoath.client.gui;

import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.CombatSnapshot;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Shows synchronized facts; the server remains responsible for authorizing casts.
final class TechniqueDetails {
    private static final int TITLE_Y = 12, LINE_HEIGHT = 11, TEXT = 0xFFF1EDE4, WARNING = 0xFFFFAD78;

    private TechniqueDetails() {}

    static void draw(GuiGraphicsExtractor graphics, Technique technique, int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        var lines = font.split(Component.translatable(technique.translationKey()), width);
        for (int index = 0; index < lines.size(); index++)
            graphics.centeredText(font, lines.get(index), x, TITLE_Y + index * LINE_HEIGHT, TEXT);
        var state = CombatInput.snapshot();
        graphics.centeredText(
                font,
                Component.translatable(
                        "gui.cursed-oath.energy", state == null ? "—" : Math.max(0, state.energy() - state.reserved())),
                x,
                y - LINE_HEIGHT,
                TEXT);
        boolean active = state != null && active(technique, state);
        int cost = technique.cost().startup() + technique.cost().release();
        var detail = active
                ? Component.translatable("gui.cursed-oath.deactivate")
                : technique == Technique.INFINITY || technique == Technique.AMPLIFICATION
                        ? Component.translatable("gui.cursed-oath.upkeep")
                        : Component.translatable("gui.cursed-oath.cost", cost);
        graphics.centeredText(font, detail, x, y + 1, TEXT);
        var warning = warning(technique, state, active);
        if (warning != null)
            graphics.centeredText(font, Component.translatable(warning), x, y + LINE_HEIGHT + 2, WARNING);
    }

    private static boolean active(Technique technique, CombatSnapshot state) {
        return switch (technique) {
            case INFINITY -> state.infinity();
            case AMPLIFICATION -> state.amplification();
            case SIMPLE_DOMAIN -> state.simpleDomain() > 0;
            default -> false;
        };
    }

    private static @Nullable String warning(Technique technique, @Nullable CombatSnapshot state, boolean active) {
        var player = Minecraft.getInstance().player;
        if (player != null && Domains.isOverloaded(player)) return "hud.cursed-oath.overloaded";
        if (state == null || active) return null;
        int cost = technique.cost().startup() + technique.cost().release();
        if (state.energy() - state.reserved() < cost) return "gui.cursed-oath.insufficient";
        if (state.amplification() && technique.innate()) return "gui.cursed-oath.amplification";
        if (state.burnout() > 0 && technique.innate()) return "gui.cursed-oath.burnout";
        return null;
    }
}
