package io.github.romeoahmed.cursedoath.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.client.gui.TechniqueWheelScreen;
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals;
import io.github.romeoahmed.cursedoath.network.CastRequest;
import io.github.romeoahmed.cursedoath.network.CombatSnapshot;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class CombatInput {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CursedOath.id("combat"));
    public static final KeyMapping SELECT = key("select", InputConstants.KEY_R),
            CAST = key("cast", InputConstants.KEY_V),
            CANCEL = key("cancel", InputConstants.KEY_X),
            WHEEL = key("wheel", InputConstants.KEY_B),
            PULSE = key("pulse", InputConstants.KEY_G);
    private static final List<Technique> TECHNIQUES = List.of(Technique.values());
    private static Technique selected = Technique.BLUE;
    private static @Nullable CombatSnapshot snapshot;
    private static long sequence;

    private CombatInput() {}

    public static Technique selected() {
        return selected;
    }

    public static @Nullable CombatSnapshot snapshot() {
        return snapshot;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshot.TYPE, (packet, context) -> {
            if (snapshot == null || !snapshot.session().equals(packet.session())) sequence = 0;
            snapshot = packet;
        });
        ClientPlayNetworking.registerGlobalReceiver(
                TechniqueEvent.TYPE, (packet, context) -> TechniqueVisuals.accept(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            snapshot = null;
            sequence = 0;
            selected = Technique.BLUE;
            TechniqueVisuals.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (WHEEL.consumeClick()) if (active(client)) client.gui.setScreen(new TechniqueWheelScreen());
            while (SELECT.consumeClick())
                if (active(client)) selected = TECHNIQUES.get((TECHNIQUES.indexOf(selected) + 1) % TECHNIQUES.size());
            while (CAST.consumeClick()) if (active(client)) send(selected.wireId());
            while (CANCEL.consumeClick()) if (active(client)) send(CastRequest.CANCEL);
            while (PULSE.consumeClick()) if (active(client)) send(CastRequest.PULSE);
        });
    }

    public static void choose(Technique technique) {
        choose(technique, false);
    }

    public static void choose(Technique technique, boolean cast) {
        selected = technique;
        if (cast) send(technique.wireId());
    }

    private static boolean active(Minecraft client) {
        return client.gui.screen() == null && client.player != null && snapshot != null && snapshot.enabled();
    }

    private static void send(int technique) {
        var state = snapshot;
        if (state != null && ClientPlayNetworking.canSend(CastRequest.TYPE))
            ClientPlayNetworking.send(new CastRequest(state.session(), sequence++, technique));
    }

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.cursed-oath." + name, InputConstants.Type.KEYBOARD, code, CATEGORY));
    }
}
