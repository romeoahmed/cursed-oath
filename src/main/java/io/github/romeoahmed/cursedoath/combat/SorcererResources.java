package io.github.romeoahmed.cursedoath.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// Persistent energy and remaining recovery/burnout ticks; active casts and reservations are transient.
/// Timers advance only while the player's combat state ticks, not while offline.
public record SorcererResources(int energy, int recovery, int burnout) {
    public SorcererResources() {
        this(CursedEnergy.CAPACITY, 0, 0);
    }

    public static final Codec<SorcererResources> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, CursedEnergy.CAPACITY).fieldOf("energy").forGetter(SorcererResources::energy),
                    Codec.intRange(0, 1200).fieldOf("recovery").forGetter(SorcererResources::recovery),
                    Codec.intRange(0, 1200).optionalFieldOf("burnout", 0).forGetter(SorcererResources::burnout))
            .apply(instance, SorcererResources::new));
}
