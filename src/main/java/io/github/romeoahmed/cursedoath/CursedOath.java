package io.github.romeoahmed.cursedoath;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.command.PracticeCommands;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CursedOath implements ModInitializer {
    public static final String MOD_ID = "cursed-oath";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TechniqueProjectiles.initialize();
        Domains.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server -> TerrainDestruction.tick());
        CombatRuntime.initialize();
        PracticeCommands.initialize();
        LOGGER.info("Cursed Oath initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
