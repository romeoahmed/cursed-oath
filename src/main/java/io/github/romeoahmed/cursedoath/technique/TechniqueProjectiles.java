package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class TechniqueProjectiles {
    public static final EntityType<TechniqueWave> WAVE = EntityType.Builder.of(TechniqueWave::new, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("technique_wave")));
    public static final EntityType<TechniqueOrb> ORB = EntityType.Builder.of(TechniqueOrb::new, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("technique_orb")));

    private TechniqueProjectiles() {}

    public static void initialize() {
        FabricEntityDataRegistry.register(
                CursedOath.id("technique_launch_position"), TechniqueProjectile.LAUNCH_POSITION_SERIALIZER);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("technique_wave"), WAVE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("technique_orb"), ORB);
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    static void release(ServerPlayer player, Technique technique, TerrainDestruction.Work work) {
        var level = player.level();
        if (technique == Technique.BLUE || technique == Technique.RED) {
            if (technique == Technique.BLUE)
                for (var entity :
                        level.getEntities(ORB, orb -> orb.getOwner() == player && orb.technique() == Technique.BLUE))
                    entity.discard();
            var orb = new TechniqueOrb(ORB, level);
            orb.configure(player, technique, work);
            if (!level.addFreshEntity(orb)) work.close();
        } else {
            var wave = new TechniqueWave(WAVE, level);
            wave.configure(player, technique, work);
            if (!level.addFreshEntity(wave)) work.close();
        }
    }
}
