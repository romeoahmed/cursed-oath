package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.CursedOath
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

object DomainSounds {
    val OPEN = register("domain_open")
    val CLOSE = register("domain_close")
    val CUT = register("shrine_cut")
    val BREAK = register("barrier_break")

    fun initialize() = Unit

    private fun register(path: String): SoundEvent {
        val id = CursedOath.id(path)
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
    }
}
