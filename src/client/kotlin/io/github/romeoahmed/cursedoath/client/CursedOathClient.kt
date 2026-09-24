package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation
import io.github.romeoahmed.cursedoath.client.hud.CombatHud
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.client.render.TechniqueProjectileRenderer
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.entity.EntityRenderers

object CursedOathClient : ClientModInitializer {
    override fun onInitializeClient() {
        EntityRenderers.register(TechniqueProjectiles.WAVE, ::TechniqueProjectileRenderer)
        EntityRenderers.register(TechniqueProjectiles.ORB, ::TechniqueProjectileRenderer)
        CastingAnimation.initialize()
        CombatInput.initialize()
        TechniqueVisuals.initialize()
        CombatHud.initialize()
    }
}
