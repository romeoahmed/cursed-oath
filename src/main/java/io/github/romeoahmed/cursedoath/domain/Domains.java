package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.SorcererAttachments;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Domains {
    private Domains() {}

    public static final EntityType<DomainEntity> TYPE = EntityType.Builder.of(DomainEntity::new, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(32)
            .updateInterval(20)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("domain")));
    private static final AttachmentType<Boolean> OVERLOADED = AttachmentRegistry.create(
            CursedOath.id("overloaded"),
            builder -> builder.initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
    private static final AttachmentType<Set<LivingEntity>> CONTROLLED =
            AttachmentRegistry.create(CursedOath.id("domain_controlled"), builder -> builder.initializer(Set::of));

    public static void initialize() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("domain"), TYPE);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof DomainEntity domain) DomainIndex.add(domain);
        });
        ServerTickEvents.END_SERVER_TICK.register(Domains::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(Domains::clear);
        DomainSounds.initialize();
        DomainInteractions.initialize();
        BarrierState.initialize();
    }

    public static List<DomainEntity> inLevel(Level level) {
        return DomainIndex.inLevel(level).stream()
                .filter(domain -> !domain.isRemoved())
                .toList();
    }

    public static boolean isOverloaded(Entity entity) {
        return Boolean.TRUE.equals(entity.getAttached(OVERLOADED));
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    public static @Nullable DomainEntity ownedBy(ServerPlayer player) {
        for (var domain : DomainIndex.inLevel(player.level()))
            if (!domain.isRemoved() && domain.caster == player) return domain;
        return null;
    }

    public static DomainEntity open(ServerPlayer player, Technique technique) {
        var existing = ownedBy(player);
        if (existing != null) end(existing);
        double radius = technique == Technique.UNLIMITED_VOID
                ? DomainEntity.VOID_RADIUS
                : player.getAttachedOrCreate(SorcererAttachments.PROFILE).domainRadius();
        var domain = new DomainEntity(TYPE, player.level());
        domain.configure(player, technique, radius);
        var fighter = CombatRuntime.fighter(player);
        domain.fighter = fighter;
        if (player.level().addFreshEntity(domain)) {
            fighter.imposeBurnout(DomainEntity.BURNOUT);
            sound(domain, DomainSounds.OPEN, 0.65f);
        }
        return domain;
    }

    public static void end(DomainEntity domain) {
        if (domain.isRemoved()) return;
        if (domain.terrain != null) domain.terrain.close();
        sound(domain, DomainSounds.CLOSE, 0.6f);
        domain.discard();
    }

    static void removed(DomainEntity domain) {
        if (domain.fighter != null) domain.fighter.imposeBurnout(DomainEntity.BURNOUT);
        // Recompute aggregate control immediately when an anchor disappears during a hit callback.
        var controlled = domain.level().globalAttachments().getAttachedOrCreate(CONTROLLED);
        for (var entity : controlled) if (!DomainEffects.overloads(entity)) entity.setAttached(OVERLOADED, false);
    }

    /// Ends this player's domains even after a dimension transfer has changed its current level.
    @SuppressWarnings("ReferenceEquality") // A replaced player with the same UUID does not own these anchors.
    public static void cancel(ServerPlayer player) {
        for (var domain : DomainIndex.all(player.level().getServer())) if (domain.caster == player) end(domain);
    }

    /// Resolves shells before sure hits, then commits aggregate control and protection erosion.
    /// World mutations stay on the server thread; damage callbacks may remove domains mid-batch.
    public static void tick(MinecraftServer server) {
        var all = DomainIndex.all(server);
        var controlled = server.globalAttachments().getAttachedOrCreate(CONTROLLED);
        if (all.isEmpty() && controlled.isEmpty()) return;
        var affected = new LinkedHashSet<LivingEntity>();
        for (var domain : all) if (!domain.valid()) end(domain);
        var groups = all.stream()
                .filter(domain -> !domain.isRemoved())
                .collect(Collectors.groupingBy(Entity::level, LinkedHashMap::new, Collectors.toList()));
        var protection = new DomainProtection();
        for (var group : groups.values()) {
            for (var domain : group) DomainEffects.erodeShells(domain, group);
            for (var domain : group) if (!domain.isRemoved()) DomainEffects.tick(domain, group, affected, protection);
        }
        affected.removeIf(entity -> !DomainEffects.overloads(entity));
        for (var entity : controlled) if (!affected.contains(entity)) entity.setAttached(OVERLOADED, false);
        for (var entity : affected) {
            if (!isOverloaded(entity)) entity.setAttached(OVERLOADED, true);
            entity.stopUsingItem();
            entity.setDeltaMovement(Vec3.ZERO);
        }
        server.globalAttachments().setAttached(CONTROLLED, affected);
        protection.finishTick();
    }

    private static void clear(MinecraftServer server) {
        for (var domain : DomainIndex.all(server)) {
            if (domain.terrain != null) domain.terrain.close();
            domain.discard();
        }
        var controlled = server.globalAttachments().removeAttached(CONTROLLED);
        if (controlled != null) for (var entity : controlled) entity.setAttached(OVERLOADED, false);
    }

    private static void sound(DomainEntity domain, SoundEvent sound, float pitch) {
        if (domain.level() instanceof ServerLevel level)
            level.playSound(null, domain.getX(), domain.getY(), domain.getZ(), sound, SoundSource.PLAYERS, 2f, pitch);
    }
}
