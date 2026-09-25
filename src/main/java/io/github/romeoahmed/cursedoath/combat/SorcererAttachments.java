package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.CursedOath;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class SorcererAttachments {
    private SorcererAttachments() {}

    public static final AttachmentType<SorcererProfile> PROFILE = AttachmentRegistry.create(
            CursedOath.id("profile"),
            builder -> builder.initializer(SorcererProfile::new)
                    .persistent(SorcererProfile.CODEC)
                    .copyOnDeath());
    public static final AttachmentType<SorcererResources> RESOURCES = AttachmentRegistry.create(
            CursedOath.id("resources"),
            builder -> builder.initializer(SorcererResources::new)
                    .persistent(SorcererResources.CODEC)
                    .copyOnDeath());

    public static void initialize() {}
}
