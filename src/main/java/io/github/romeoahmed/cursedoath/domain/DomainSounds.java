package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class DomainSounds {
    private DomainSounds() {}

    public static final SoundEvent OPEN = register("domain_open"),
            CLOSE = register("domain_close"),
            CUT = register("shrine_cut"),
            BREAK = register("barrier_break");

    public static void initialize() {}

    private static SoundEvent register(String path) {
        var id = CursedOath.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
