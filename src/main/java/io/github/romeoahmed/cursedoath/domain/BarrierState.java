package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;

/// Synced, unsaved defense state: `0` is inactive, positive values are Simple Domain strength,
/// and `-1` is Domain Amplification.
public final class BarrierState {
    private BarrierState() {}

    private static final AttachmentType<Integer> STATE = AttachmentRegistry.create(
            CursedOath.id("barrier_state"),
            builder -> builder.initializer(() -> 0).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    public static void initialize() {}

    public static int get(Entity entity) {
        var state = entity.getAttached(STATE);
        return state == null ? 0 : state;
    }

    public static void update(Entity entity, int value) {
        if (get(entity) != value) entity.setAttached(STATE, value);
    }
}
