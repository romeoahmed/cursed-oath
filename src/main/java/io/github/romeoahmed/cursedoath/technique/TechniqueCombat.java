package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class TechniqueCombat {
    private static final float HEAL_AMOUNT = 6,
            RELEASE_VOLUME = 0.8f,
            RELEASE_PITCH = 1.2f,
            BLACK_FLASH_VOLUME = 1.2f,
            BLACK_FLASH_PITCH = 0.65f;
    private static final double EVENT_RANGE = 96, MAX_SPEED = 5;

    private TechniqueCombat() {}

    public static void release(ServerPlayer player, UUID id, Technique technique) {
        release(player, id, technique, null);
    }

    public static void release(
            ServerPlayer player, UUID id, Technique technique, TerrainDestruction.@Nullable Work terrain) {
        var level = player.level();
        var work =
                technique.destroysTerrain() ? (terrain == null ? TerrainDestruction.reserve(player) : terrain) : null;
        if (technique.destroysTerrain() && work == null) return;
        var end = apply(player, technique, work);
        if (end == null) {
            event(player, id, technique, TechniqueEvent.CANCEL, player.getEyePosition());
            return;
        }
        event(player, id, technique, TechniqueEvent.RELEASE, end);
        if (technique.domain()) return;
        var sound = switch (technique) {
            case BLUE -> SoundEvents.BEACON_ACTIVATE;
            case RED -> SoundEvents.FIRECHARGE_USE;
            case PURPLE -> SoundEvents.GENERIC_EXPLODE.value();
            case HEAL -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
        level.playSound(null, end.x, end.y, end.z, sound, SoundSource.PLAYERS, RELEASE_VOLUME, RELEASE_PITCH);
    }

    private static @Nullable Vec3 apply(
            ServerPlayer player, Technique technique, TerrainDestruction.@Nullable Work work) {
        return switch (technique) {
            case BLUE, RED, PURPLE, DISMANTLE -> {
                TechniqueProjectiles.release(player, technique, Objects.requireNonNull(work));
                yield player.getEyePosition();
            }
            case CLEAVE -> CleaveContact.release(player, Objects.requireNonNull(work));
            case HEAL -> {
                player.heal(HEAL_AMOUNT);
                yield player.position().add(0, 1, 0);
            }
            case UNLIMITED_VOID, MALEVOLENT_SHRINE ->
                Domains.open(player, technique).position();
            case INFINITY, SIMPLE_DOMAIN, AMPLIFICATION -> null;
        };
    }

    public static void blackFlash(ServerPlayer player, LivingEntity target) {
        event(
                player,
                UUID.randomUUID(),
                Technique.CLEAVE,
                TechniqueEvent.BLACK_FLASH,
                target.getBoundingBox().getCenter());
        player.level()
                .playSound(
                        null,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS,
                        BLACK_FLASH_VOLUME,
                        BLACK_FLASH_PITCH);
    }

    public static void event(ServerPlayer player, UUID id, Technique technique, int stage, Vec3 end) {
        var level = player.level();
        // Contact can coincide with the eyes; preserve its cutting direction even then.
        var origin = technique == Technique.CLEAVE && stage == TechniqueEvent.RELEASE
                ? end.subtract(player.getLookAngle())
                : player.getEyePosition();
        event(
                player,
                new TechniqueEvent(
                        level.dimension().identifier(),
                        id,
                        player.getId(),
                        technique.wireId(),
                        stage,
                        level.getGameTime(),
                        origin,
                        end));
    }

    public static void event(ServerPlayer player, TechniqueEvent payload) {
        for (var viewer : PlayerLookup.around(player.level(), payload.destination(), EVENT_RANGE))
            if (ServerPlayNetworking.canSend(viewer, TechniqueEvent.TYPE)) ServerPlayNetworking.send(viewer, payload);
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    public static boolean canAffect(ServerPlayer owner, LivingEntity target) {
        return target.isAlive()
                && !target.isSpectator()
                && owner != target
                && !owner.isAlliedTo(target)
                && (!(target instanceof ServerPlayer player) || (!player.isCreative() && owner.canHarmPlayer(player)));
    }

    public static boolean hasInfinity(LivingEntity target) {
        return target instanceof ServerPlayer player && CombatRuntime.hasInfinity(player);
    }

    @SuppressWarnings("ReferenceEquality") // Caster-bound attacks cannot follow a transferred player.
    static boolean isActive(ServerPlayer player, ServerLevel level) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && player.level() == level;
    }

    public static @Nullable HitResult contact(ServerPlayer player, double range) {
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(range));
        var level = player.level();
        if (!LoadedChunks.contains(level, new AABB(start, end))) return null;
        var block = level.clipIncludingBorder(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        // Native intersection includes rays starting inside an entity.
        HitResult nearest = block;
        double distance = Double.POSITIVE_INFINITY;
        for (var hit : ProjectileUtil.getManyEntityHitResult(
                level,
                player,
                start,
                block.getLocation(),
                new AABB(start, block.getLocation()),
                entity -> entity instanceof LivingEntity living && canAffect(player, living),
                0f,
                ClipContext.Block.COLLIDER,
                true,
                true)) {
            double candidate = hit.getLocation().distanceToSqr(start);
            if (candidate < distance) {
                nearest = hit;
                distance = candidate;
            }
        }
        return nearest;
    }

    static void push(LivingEntity target, Vec3 force) {
        target.push(force);
        var velocity = target.getDeltaMovement();
        if (velocity.lengthSqr() > MAX_SPEED * MAX_SPEED)
            target.setDeltaMovement(velocity.normalize().scale(MAX_SPEED));
        target.syncVelocity = true;
    }
}
