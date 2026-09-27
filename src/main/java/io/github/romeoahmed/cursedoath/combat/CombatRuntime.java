package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.CastRequest;
import io.github.romeoahmed.cursedoath.network.CombatSnapshot;
import io.github.romeoahmed.cursedoath.network.RequestGate;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.InfinityDefense;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/// Server-thread combat coordination. Each server owns its fighters, connections and last sent snapshots.
public final class CombatRuntime {
    private static final long SYNC_INTERVAL = 4;
    private static final AttachmentType<State> STATE =
            AttachmentRegistry.create(CursedOath.id("combat_runtime"), builder -> builder.initializer(State::new));

    private static final class State {
        final IdentityHashMap<ServerLevel, Map<UUID, Fighter>> worlds = new IdentityHashMap<>();
        final Map<UUID, Connection> connections = new HashMap<>();
    }

    private static final class Connection {
        final RequestGate gate = new RequestGate();

        @Nullable
        CombatSnapshot snapshot;
    }

    private static State state(ServerLevel level) {
        return level.globalAttachments().getAttachedOrCreate(STATE);
    }

    private CombatRuntime() {}

    public static void initialize() {
        SorcererAttachments.initialize();
        PayloadTypeRegistry.serverboundPlay().register(CastRequest.TYPE, CastRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshot.TYPE, CombatSnapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TechniqueEvent.TYPE, TechniqueEvent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                CastRequest.TYPE, (request, context) -> request(context.player(), request));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            state(handler.player.level()).connections.put(handler.player.getUUID(), new Connection());
            sync(fighter(handler.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            remove(handler.player);
            state(handler.player.level()).connections.remove(handler.player.getUUID());
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
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> resync(player));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, old, next) -> resync(player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            var attacker = source.getEntity();
            return (attacker == null || !Domains.isOverloaded(attacker))
                    && (!(entity instanceof ServerPlayer player) || !InfinityDefense.blocks(player, source));
        });
        ServerTickEvents.END_LEVEL_TICK.register(CombatRuntime::tick);
    }

    /// Gets or creates state for this live player in its current world, retiring any previous owner of the UUID.
    /// Retirement cancels active casts and domains; saved costs, recovery and burnout remain on the player.
    @SuppressWarnings("ReferenceEquality") // Live world/player instances define ownership across transfers.
    public static Fighter fighter(ServerPlayer player) {
        var worlds = state(player.level()).worlds;
        // A dimension transfer may retain the same player instance; retire its old world state first.
        for (var entry : worlds.entrySet())
            if (entry.getKey() != player.level()) {
                var old = entry.getValue().remove(player.getUUID());
                if (old != null) old.cancel();
            }
        var fighters = worlds.computeIfAbsent(player.level(), ignored -> new HashMap<>());
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

    @SuppressWarnings("ReferenceEquality") // A retired player cannot borrow the replacement's defenses.
    private static @Nullable Fighter existing(ServerPlayer player) {
        var fighters = state(player.level()).worlds.get(player.level());
        var fighter = fighters == null ? null : fighters.get(player.getUUID());
        return fighter != null && fighter.player() == player ? fighter : null;
    }

    public static boolean hasInfinity(ServerPlayer player) {
        var fighter = existing(player);
        return fighter != null
                && fighter.infinity()
                && player.isAlive()
                && !player.isRemoved()
                && !player.isSpectator();
    }

    public static boolean hasAmplification(ServerPlayer player) {
        var fighter = existing(player);
        return fighter != null
                && fighter.defense().amplification()
                && player.isAlive()
                && !player.isRemoved()
                && !player.isSpectator();
    }

    /// Returns a live collection for server-thread queries; callers must not mutate it.
    /// Copy before combat callbacks, which may remove or replace fighters.
    public static Collection<Fighter> fighters(ServerLevel level) {
        var fighters = state(level).worlds.get(level);
        return fighters == null ? List.of() : fighters.values();
    }

    private static void request(ServerPlayer player, CastRequest request) {
        var connection = state(player.level()).connections.get(player.getUUID());
        if (connection == null
                || !connection.gate.accept(
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
        var fighters = state(level).worlds.get(level);
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

    @SuppressWarnings("ReferenceEquality") // Late callbacks from an old entity must not retire its replacement.
    private static void remove(ServerPlayer player) {
        var state = state(player.level());
        var connection = state.connections.get(player.getUUID());
        if (connection != null) connection.snapshot = null;
        for (var fighters : state.worlds.values()) {
            var fighter = fighters.get(player.getUUID());
            if (fighter != null && fighter.player() == player) {
                fighters.remove(player.getUUID());
                fighter.cancel();
            }
        }
    }

    private static void resync(ServerPlayer player) {
        var connection = state(player.level()).connections.get(player.getUUID());
        if (connection != null) connection.snapshot = null;
        sync(fighter(player));
    }

    private static void sync(Fighter fighter) {
        var player = fighter.player();
        var connection = state(player.level()).connections.get(player.getUUID());
        if (connection == null || !ServerPlayNetworking.canSend(player, CombatSnapshot.TYPE)) return;
        var cast = fighter.cast();
        var snapshot = new CombatSnapshot(
                connection.gate.session(),
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
        if (!snapshot.equals(connection.snapshot)) {
            connection.snapshot = snapshot;
            ServerPlayNetworking.send(player, snapshot);
        }
    }
}
