package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.CastRequest;
import io.github.romeoahmed.cursedoath.network.CombatSnapshot;
import io.github.romeoahmed.cursedoath.network.RequestGate;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.InfinityDefense;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class CombatRuntime {
    private static final long SYNC_INTERVAL = 4;
    private static final IdentityHashMap<ServerLevel, Map<UUID, Fighter>> WORLDS = new IdentityHashMap<>();
    private static final Map<UUID, RequestGate> CONNECTIONS = new HashMap<>();
    private static final Map<UUID, CombatSnapshot> SNAPSHOTS = new HashMap<>();

    private CombatRuntime() {}

    public static void initialize() {
        SorcererAttachments.initialize();
        PayloadTypeRegistry.serverboundPlay().register(CastRequest.TYPE, CastRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshot.TYPE, CombatSnapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TechniqueEvent.TYPE, TechniqueEvent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                CastRequest.TYPE, (request, context) -> request(context.player(), request));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CONNECTIONS.put(handler.player.getUUID(), new RequestGate());
            sync(fighter(handler.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            remove(handler.player);
            CONNECTIONS.remove(handler.player.getUUID());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) remove(player);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, amount, blocked) -> {
            if (entity instanceof ServerPlayer player && amount > 0) {
                var fighter = existing(player);
                var cast = fighter == null ? null : fighter.cast();
                if (fighter != null && cast != null && !cast.technique().domain()) {
                    fighter.cancel();
                    sync(fighter);
                }
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
            SNAPSHOTS.remove(player.getUUID());
            sync(fighter(player));
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, old, next) -> {
            SNAPSHOTS.remove(player.getUUID());
            sync(fighter(player));
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            var attacker = source.getEntity();
            return (attacker == null || !Domains.isOverloaded(attacker))
                    && (!(entity instanceof ServerPlayer player) || !InfinityDefense.blocks(player, source));
        });
        ServerTickEvents.END_LEVEL_TICK.register(CombatRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WORLDS.clear();
            CONNECTIONS.clear();
            SNAPSHOTS.clear();
            TerrainDestruction.clear();
        });
    }

    @SuppressWarnings("ReferenceEquality") // Live world/player instances define ownership across transfers.
    public static Fighter fighter(ServerPlayer player) {
        // A dimension transfer may retain the same player instance; retire its old world state first.
        for (var entry : WORLDS.entrySet())
            if (entry.getKey() != player.level()) {
                var old = entry.getValue().remove(player.getUUID());
                if (old != null) old.cancel();
            }
        var fighters = WORLDS.computeIfAbsent(player.level(), ignored -> new HashMap<>());
        var previous = fighters.get(player.getUUID());
        if (previous != null && previous.player() == player) return previous;
        if (previous != null) previous.cancel();
        var fighter = new Fighter(player);
        fighters.put(player.getUUID(), fighter);
        return fighter;
    }

    public static void practice(ServerPlayer player, boolean enabled) {
        remove(player);
        player.setAttached(
                SorcererAttachments.PROFILE, enabled ? SorcererProfile.practiceProfile() : new SorcererProfile());
        // Only the practice command resets resources; login and respawn retain the saved balance.
        if (enabled) player.setAttached(SorcererAttachments.RESOURCES, new SorcererResources());
        sync(fighter(player));
    }

    private static @Nullable Fighter existing(ServerPlayer player) {
        var fighters = WORLDS.get(player.level());
        return fighters == null ? null : fighters.get(player.getUUID());
    }

    public static boolean hasInfinity(ServerPlayer player) {
        var fighter = existing(player);
        return fighter != null && fighter.infinity() && player.isAlive() && !player.isSpectator();
    }

    public static Collection<Fighter> fighters(ServerLevel level) {
        var fighters = WORLDS.get(level);
        return fighters == null ? List.of() : fighters.values();
    }

    private static void request(ServerPlayer player, CastRequest request) {
        var gate = CONNECTIONS.get(player.getUUID());
        if (gate == null
                || !gate.accept(
                        request.session(), request.sequence(), player.level().getGameTime())) return;
        var fighter = fighter(player);
        var profile = player.getAttachedOrCreate(SorcererAttachments.PROFILE);
        if (!profile.practice() || !player.isAlive() || player.isSpectator()) return;
        switch (request.technique()) {
            case CastRequest.CANCEL -> fighter.cancel();
            case CastRequest.PULSE -> fighter.preparePulse();
            default -> {
                var technique = Technique.fromWire(request.technique());
                if (technique != null) fighter.prepare(technique);
            }
        }
        sync(fighter);
    }

    @SuppressWarnings("ReferenceEquality") // Replaced or transferred instances must not keep ticking.
    private static void tick(ServerLevel level) {
        var fighters = WORLDS.get(level);
        if (fighters == null) return;
        // Released hits can synchronously remove fighters through AFTER_DEATH.
        for (var fighter : List.copyOf(fighters.values())) {
            var player = fighter.player();
            if (fighters.get(player.getUUID()) != fighter) continue;
            if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                fighter.cancel();
                fighters.remove(player.getUUID());
            } else {
                fighter.tick();
                if (level.getGameTime() % SYNC_INTERVAL == 0) sync(fighter);
            }
        }
    }

    public static boolean consumePulse(ServerPlayer player) {
        var fighter = existing(player);
        return fighter != null && fighter.consumePulse();
    }

    private static void remove(ServerPlayer player) {
        SNAPSHOTS.remove(player.getUUID());
        for (var fighters : WORLDS.values()) {
            var fighter = fighters.remove(player.getUUID());
            if (fighter != null) fighter.cancel();
        }
    }

    private static void sync(Fighter fighter) {
        var player = fighter.player();
        var gate = CONNECTIONS.get(player.getUUID());
        if (gate == null || !ServerPlayNetworking.canSend(player, CombatSnapshot.TYPE)) return;
        var cast = fighter.cast();
        var snapshot = new CombatSnapshot(
                gate.session(),
                player.getAttachedOrCreate(SorcererAttachments.PROFILE).practice(),
                fighter.energy().current(),
                fighter.energy().reserved(),
                fighter.recovery(),
                cast == null ? 0 : cast.technique().wireId(),
                fighter.infinity(),
                fighter.pulseReady(),
                Domains.ownedBy(player) == null ? fighter.burnout() : 0,
                fighter.defense().simple(),
                fighter.defense().amplification());
        if (!snapshot.equals(SNAPSHOTS.put(player.getUUID(), snapshot))) ServerPlayNetworking.send(player, snapshot);
    }
}
