package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.command.PracticeCommands
import io.github.romeoahmed.cursedoath.technique.BlueFields
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object CursedOath : ModInitializer {
    const val MOD_ID: String = "cursed-oath"

    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        TechniqueProjectiles.initialize()
        ServerTickEvents.END_LEVEL_TICK.register(TerrainDestruction::tick)
        ServerTickEvents.END_LEVEL_TICK.register(BlueFields::tick)
        CombatRuntime.initialize()
        PracticeCommands.initialize()
        LOGGER.info("Cursed Oath initialized")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
