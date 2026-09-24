package io.github.romeoahmed.cursedoath.client.input

import com.mojang.blaze3d.platform.InputConstants
import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals
import io.github.romeoahmed.cursedoath.network.CastRequest
import io.github.romeoahmed.cursedoath.network.CombatSnapshot
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object CombatInput {
    private val category = KeyMapping.Category.register(CursedOath.id("combat"))
    val select = key("select", InputConstants.KEY_R)
    val cast = key("cast", InputConstants.KEY_V)
    val cancel = key("cancel", InputConstants.KEY_X)
    val pulse = key("pulse", InputConstants.KEY_G)
    var selected = Technique.BLUE
        private set
    var snapshot: CombatSnapshot? = null
        private set
    private var sequence = 0L

    fun initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CombatSnapshot.TYPE) { packet, _ ->
            if (snapshot?.session != packet.session) sequence = 0
            snapshot = packet
        }
        ClientPlayNetworking.registerGlobalReceiver(TechniqueEvent.TYPE) { packet, _ ->
            TechniqueVisuals.accept(packet)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            snapshot = null
            sequence = 0
            selected = Technique.BLUE
            TechniqueVisuals.clear()
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (select.consumeClick()) {
                if (active(client)) selected = Technique.entries[(selected.ordinal + 1) % Technique.entries.size]
            }
            while (cast.consumeClick()) if (active(client)) send(selected.wireId)
            while (cancel.consumeClick()) if (active(client)) send(CastRequest.CANCEL)
            while (pulse.consumeClick()) if (active(client)) send(CastRequest.PULSE)
        }
    }

    private fun active(client: Minecraft) =
        client.gui.screen() == null && client.player != null && snapshot?.enabled == true

    private fun send(technique: Int) {
        val state = snapshot ?: return
        if (ClientPlayNetworking.canSend(CastRequest.TYPE)) {
            ClientPlayNetworking.send(CastRequest(state.session, sequence++, technique))
        }
    }

    private fun key(
        name: String,
        code: Int,
    ): KeyMapping =
        KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.cursed-oath.$name", InputConstants.Type.KEYBOARD, code, category),
        )
}
