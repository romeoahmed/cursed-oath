package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/// Each level owns its transient index; client and integrated server never share mutable sets.
public final class DomainIndex {
    private DomainIndex() {}

    private static final AttachmentType<Set<DomainEntity>> INDEX =
            AttachmentRegistry.create(CursedOath.id("domains"), builder -> builder.initializer(LinkedHashSet::new));

    /// Snapshots this server's level indexes before callbacks can remove anchors.
    public static List<DomainEntity> all(MinecraftServer server) {
        return StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .flatMap(level -> inLevel(level).stream())
                .toList();
    }

    public static void add(DomainEntity domain) {
        domain.level().getAttachedOrCreate(INDEX).add(domain);
    }

    public static void remove(DomainEntity domain) {
        var index = domain.level().getAttached(INDEX);
        if (index != null) index.remove(domain);
    }

    /// Returns the level's live index for queries on its owning thread; callers must not mutate it.
    /// Copy before callbacks that can remove anchors; [#all(MinecraftServer)] snapshots a server-wide batch.
    public static Collection<DomainEntity> inLevel(Level level) {
        var index = level.getAttached(INDEX);
        return index == null ? List.of() : index;
    }
}
