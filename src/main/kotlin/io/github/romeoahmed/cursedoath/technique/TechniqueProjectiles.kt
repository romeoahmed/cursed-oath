package io.github.romeoahmed.cursedoath.technique

import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object TechniqueProjectiles {
    val WAVE: EntityType<TechniqueWave> =
        EntityType.Builder
            .of(::TechniqueWave, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("technique_wave")))

    val ORB: EntityType<TechniqueOrb> =
        EntityType.Builder
            .of(::TechniqueOrb, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("technique_orb")))

    fun initialize() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("technique_wave"), WAVE)
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("technique_orb"), ORB)
    }

    internal fun release(
        player: ServerPlayer,
        technique: Technique,
        work: TerrainDestruction.Work,
    ) {
        val level = player.level()
        if (technique == Technique.BLUE || technique == Technique.RED) {
            if (technique == Technique.BLUE) {
                level
                    .getEntities(ORB) { it.getOwner() === player && it.technique == Technique.BLUE }
                    .forEach { it.discard() }
            }
            val orb = TechniqueOrb(ORB, level)
            orb.configure(player, technique, work)
            if (level.addFreshEntity(orb)) orb.activate(player) else work.close()
        } else {
            val wave = TechniqueWave(WAVE, level)
            wave.configure(player, technique, work)
            if (!level.addFreshEntity(wave)) work.close()
        }
    }
}
