package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.domain.BarrierState;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/// Transient combat state for one player entity in one server level.
public final class Fighter {
    public static final class Cast {
        private final UUID id;
        private final Technique technique;
        private int remaining;
        private final TerrainDestruction.@Nullable Work terrain;

        public TerrainDestruction.@Nullable Work terrain() {
            return terrain;
        }

        private Cast(UUID id, Technique technique, int remaining, TerrainDestruction.@Nullable Work terrain) {
            this.id = id;
            this.technique = technique;
            this.remaining = remaining;
            this.terrain = terrain;
        }

        public UUID id() {
            return id;
        }

        public Technique technique() {
            return technique;
        }

        public int remaining() {
            return remaining;
        }
    }

    private static final int PULSE_COST = 5;
    private static final long PULSE_WINDOW = 60;
    private static final int RECOVERY_PER_TICK = 2;
    private final ServerPlayer player;
    private CursedEnergy energy;
    private int recovery;
    private @Nullable Cast cast;
    private final Defense defense;
    private int burnout;
    private @Nullable Long pulseAt;

    public Fighter(ServerPlayer player) {
        this.player = player;
        var saved = player.getAttachedOrCreate(SorcererAttachments.RESOURCES);
        energy = new CursedEnergy(saved.energy());
        recovery = saved.recovery();
        burnout = saved.burnout();
        defense = new Defense(player);
    }

    public ServerPlayer player() {
        return player;
    }

    public CursedEnergy energy() {
        return energy;
    }

    public int recovery() {
        return recovery;
    }

    public @Nullable Cast cast() {
        return cast;
    }

    public Defense defense() {
        return defense;
    }

    public boolean infinity() {
        return defense.infinity();
    }

    public int burnout() {
        return burnout;
    }

    public boolean pulseReady() {
        var at = pulseAt;
        if (at == null) return false;
        long age = player.level().getGameTime() - at;
        return age >= 0 && age <= PULSE_WINDOW;
    }

    public void prepare(Technique technique) {
        if (technique.domain() && Domains.ownedBy(player) != null) {
            Domains.cancel(player);
            return;
        }
        if (technique == Technique.SIMPLE_DOMAIN || technique == Technique.AMPLIFICATION) {
            prepareDefense(technique);
            return;
        }
        if (technique == Technique.INFINITY && infinity()) defense.infinity(false);
        else {
            var rejection = CastRules.rejection(this, technique);
            if (rejection != null)
                player.sendOverlayMessage(Component.translatable("message.cursed-oath." + rejection));
            else if (technique == Technique.INFINITY) defense.infinity(true);
            else beginCast(technique);
        }
    }

    private void beginCast(Technique technique) {
        var prepared =
                energy.prepare(technique.cost().startup(), technique.cost().release());
        if (prepared == null) {
            player.sendOverlayMessage(Component.translatable("message.cursed-oath.energy"));
            return;
        }
        var terrain = technique.destroysTerrain() ? TerrainDestruction.reserve(player) : null;
        if (technique.destroysTerrain() && terrain == null) {
            player.sendOverlayMessage(Component.translatable("message.cursed-oath.capacity"));
            return;
        }
        energy = prepared;
        var next = new Cast(UUID.randomUUID(), technique, technique.preparation(), terrain);
        cast = next;
        pulseAt = null;
        // Save the obligation before release; reconnecting cannot erase recovery.
        recovery = technique.preparation() + technique.recovery();
        persist();
        TechniqueCombat.event(player, next.id, technique, TechniqueEvent.PREPARE, player.getEyePosition());
    }

    private void prepareDefense(Technique technique) {
        if (!player.isAlive() || player.isSpectator() || Domains.isOverloaded(player)) return;
        if (!player.getAttachedOrCreate(SorcererAttachments.PROFILE).barriers()) return;
        energy = defense.toggle(technique, energy);
        if (defense.amplification()) cancelPreparation();
        persist();
    }

    public void imposeBurnout(int ticks) {
        burnout = Math.max(burnout, ticks);
        if (Domains.ownedBy(player) == null) defense.infinity(false);
        persist();
    }

    public void preparePulse() {
        if (!CastRules.available(player, Technique.CLEAVE) || cast != null || pulseReady()) return;
        var paid = energy.spend(PULSE_COST);
        if (paid == null) return;
        energy = paid;
        pulseAt = player.level().getGameTime();
        persist();
    }
    /// Consumes melee readiness for a primary attack or an empty swing, never a sweep.
    ///
    /// @return whether an unexpired preparation was available to the living, nonspectating player
    public boolean consumePulse() {
        boolean ready = pulseReady() && cast == null && player.isAlive() && !player.isSpectator();
        pulseAt = null;
        return ready;
    }

    public void tick() {
        if (!player.isAlive() || player.isSpectator()) {
            cancel();
            return;
        }
        if (recovery > 0) recovery--;
        if (burnout > 0 && Domains.ownedBy(player) == null) burnout--;
        var at = pulseAt;
        if (at != null && player.level().getGameTime() - at > PULSE_WINDOW) pulseAt = null;
        energy = defense.tick(energy);
        advanceCast();
        boolean resting = cast == null && recovery == 0 && !defense.sustained();
        if (resting && Domains.ownedBy(player) == null) energy = energy.recover(RECOVERY_PER_TICK);
        persist();
    }

    private void advanceCast() {
        var active = cast;
        if (active == null) return;
        boolean available =
                CastRules.available(player, active.technique) && CastRules.qualified(player, active.technique);
        if (!available || (active.terrain != null && active.terrain.finished())) cancel();
        else if (--active.remaining <= 0) {
            energy = energy.release(active.technique.cost().release());
            cast = null;
            persist();
            TechniqueCombat.release(player, active.id, active.technique, active.terrain);
        }
    }

    private void cancelPreparation() {
        var active = cast;
        if (active != null) {
            if (active.terrain != null) active.terrain.close();
            energy = energy.cancel(active.technique.cost().release());
            TechniqueCombat.event(player, active.id, active.technique, TechniqueEvent.CANCEL, player.getEyePosition());
        }
        cast = null;
        pulseAt = null;
        persist();
    }

    public void cancel() {
        cancelPreparation();
        defense.clear();
        Domains.cancel(player);
        persist();
    }

    private void persist() {
        BarrierState.update(player, defense.amplification() ? -1 : defense.simple());
        var saved = player.getAttached(SorcererAttachments.RESOURCES);
        if (saved == null
                || saved.energy() != energy.current()
                || saved.recovery() != recovery
                || saved.burnout() != burnout)
            player.setAttached(
                    SorcererAttachments.RESOURCES, new SorcererResources(energy.current(), recovery, burnout));
    }
}
