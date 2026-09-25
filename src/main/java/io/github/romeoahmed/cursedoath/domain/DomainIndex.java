package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.Level;

/// Each level owns its transient index; client and integrated server never share mutable sets.
public final class DomainIndex {
    private DomainIndex() {}

    private static final AttachmentType<Set<DomainEntity>> INDEX =
            AttachmentRegistry.create(CursedOath.id("domains"), builder -> builder.initializer(LinkedHashSet::new));
    private static final Set<DomainEntity> SERVER = new LinkedHashSet<>();

    public static List<DomainEntity> all() {
        return List.copyOf(SERVER);
    }

    public static void add(DomainEntity domain) {
        domain.level().getAttachedOrCreate(INDEX).add(domain);
        if (!domain.level().isClientSide()) SERVER.add(domain);
    }

    public static void remove(DomainEntity domain) {
        var index = domain.level().getAttached(INDEX);
        if (index != null) index.remove(domain);
        if (!domain.level().isClientSide()) SERVER.remove(domain);
    }

    public static Collection<DomainEntity> inLevel(Level level) {
        var index = level.getAttached(INDEX);
        return index == null ? List.of() : index;
    }
}
