package io.github.romeoahmed.cursedoath.client

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation
import io.github.romeoahmed.cursedoath.client.gui.CombatHud
import io.github.romeoahmed.cursedoath.client.input.CombatInput
import io.github.romeoahmed.cursedoath.client.render.TechniqueProjectileRenderer
import io.github.romeoahmed.cursedoath.client.render.TechniqueVisuals
import io.github.romeoahmed.cursedoath.client.render.domain.BarrierRenderer
import io.github.romeoahmed.cursedoath.client.render.domain.DomainRenderer
import io.github.romeoahmed.cursedoath.client.sound.DomainAudio
import io.github.romeoahmed.cursedoath.domain.DomainEntity
import io.github.romeoahmed.cursedoath.domain.DomainIndex
import io.github.romeoahmed.cursedoath.domain.Domains
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.renderer.entity.EntityRenderers

object CursedOathClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ -> if (entity is DomainEntity) DomainIndex.add(entity) }
        EntityRenderers.register(TechniqueProjectiles.WAVE, ::TechniqueProjectileRenderer)
        EntityRenderers.register(TechniqueProjectiles.ORB, ::TechniqueProjectileRenderer)
        EntityRenderers.register(Domains.TYPE, ::DomainRenderer)
        CastingAnimation.initialize()
        CombatInput.initialize()
        TechniqueVisuals.initialize()
        CombatHud.initialize()
        BarrierRenderer.initialize()
        DomainAudio.initialize()
    }
}
