package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.world.SweptVolume;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DomainInteractions {
    private static final double CONTACT_MARGIN = 0.01;
    private static final float PROJECTILE_POWER = 4f, MIN_ATTACK = 2f, ATTACK_PARTIAL_TICK = 0.5f;

    private DomainInteractions() {}

    public static void initialize() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !Domains.isOverloaded(player));
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                contact(player, player.getEyePosition(), target.getBoundingBox().getCenter()) != null
                        ? InteractionResult.FAIL
                        : blocked(player));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> blocked(player));
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> blocked(player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> blocked(player));
        UseItemCallback.EVENT.register((player, level, hand) -> blocked(player));
    }

    private static InteractionResult blocked(Entity entity) {
        return Domains.isOverloaded(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
    }
    /// Clips requested movement against closed shells on both sides; overload returns zero displacement.
    ///
    /// @param entity moving entity, used to check overload and barrier exemptions
    /// @param requested displacement before barrier clipping
    /// @return permitted displacement
    public static Vec3 movement(Entity entity, Vec3 requested) {
        if (Domains.isOverloaded(entity)) return Vec3.ZERO;
        if (entity instanceof DomainEntity
                || entity.isSpectator()
                || requested.lengthSqr() == 0
                || (entity instanceof Player player && player.isCreative())) return requested;
        var domains = DomainIndex.inLevel(entity.level());
        if (domains.isEmpty()) return requested;
        var result = requested;
        var start = entity.getBoundingBox().getCenter();
        for (var domain : domains) {
            if (domain.isRemoved() || !domain.closed() || exempt(entity, domain)) continue;
            var crossing = DomainBoundary.crossing(start, start.add(result), domain.position(), domain.radius());
            if (crossing != null) result = result.scale(Math.max(0, crossing - CONTACT_MARGIN / result.length()));
        }
        return result;
    }

    private static boolean exempt(Entity entity, DomainEntity domain) {
        if (entity.getId() == domain.ownerId()) return true;
        return domain.level().getEntity(domain.ownerId()) instanceof Player owner
                && entity instanceof Player player
                && (!owner.canHarmPlayer(player) || owner.isAlliedTo(player));
    }

    public static boolean projectile(Projectile projectile) {
        if (projectile.level().isClientSide()
                || DomainIndex.inLevel(projectile.level()).isEmpty()) return false;
        var motion = projectile.getDeltaMovement();
        var start = projectile.position();
        var owner = projectile.getOwner();
        var hit = contact(projectile.level(), owner == null ? projectile : owner, start, start.add(motion));
        if (hit == null) return false;
        var obstruction = projectile
                .level()
                .clipIncludingBorder(new ClipContext(
                        start, hit.point(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        if (obstruction.getLocation().distanceToSqr(start) < hit.point().distanceToSqr(start)) return false;
        hit.domain()
                .damageShell(
                        Math.max(MIN_ATTACK, (float) motion.length() * PROJECTILE_POWER),
                        !hit.domain().contains(start));
        projectile.discard();
        return true;
    }
    /// Resolves an empty swing against a shell using native reach, attack strength, and block occlusion.
    ///
    /// @param player attacker, before vanilla resets accumulated attack strength
    public static void punch(ServerPlayer player) {
        if (Domains.isOverloaded(player) || player.isSpectator()) return;
        if (DomainIndex.inLevel(player.level()).stream().noneMatch(domain -> !domain.isRemoved() && domain.closed()))
            return;
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        strike(
                player,
                player.getEyePosition(),
                player.level()
                        .clipIncludingBorder(new ClipContext(
                                player.getEyePosition(),
                                player.getEyePosition()
                                        .add(player.getLookAngle().scale(reach)),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                player))
                        .getLocation(),
                Math.max(MIN_ATTACK, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                        * player.getAttackStrengthScale(ATTACK_PARTIAL_TICK));
    }

    public static boolean strike(Entity owner, Vec3 start, Vec3 end, float damage) {
        var hit = contact(owner, start, end);
        if (hit == null) return false;
        hit.domain().damageShell(damage, !hit.domain().contains(start));
        return true;
    }

    public record Contact(DomainEntity domain, Vec3 point) {}

    public static @Nullable Contact contact(Entity owner, Vec3 start, Vec3 end) {
        return contact(owner.level(), owner, start, end);
    }

    private static @Nullable Contact contact(Level level, Entity owner, Vec3 start, Vec3 end) {
        DomainEntity nearest = null;
        double fraction = Double.POSITIVE_INFINITY;
        for (var domain : DomainIndex.inLevel(level)) {
            if (domain.isRemoved() || !domain.closed() || exempt(owner, domain)) continue;
            var crossing = DomainBoundary.crossing(start, end, domain.position(), domain.radius());
            if (crossing != null && crossing < fraction) {
                nearest = domain;
                fraction = crossing;
            }
        }
        return nearest == null ? null : new Contact(nearest, start.lerp(end, fraction));
    }
    /// Damages closed shells touched by a swept sphere, including grazes missed by its center line.
    ///
    /// @param level flight world, which may differ from the owner's current world
    /// @param owner attacker used for barrier exemptions
    /// @param volume segment supplying the sphere's center path
    /// @param radius sphere radius in blocks
    /// @param damage shell damage before the interior-attack reduction
    /// @param hit mutable per-flight IDs; already hit shells are skipped and new hits are added
    public static void pierce(
            Level level, Entity owner, SweptVolume volume, double radius, float damage, Set<UUID> hit) {
        var start = volume.start();
        var end = volume.end();
        var motion = end.subtract(start);
        for (var domain : Domains.inLevel(level)) {
            if (!domain.closed() || exempt(owner, domain) || hit.contains(domain.getUUID())) continue;
            double fraction = Math.clamp(
                    domain.position().subtract(start).dot(motion) / Math.max(CONTACT_MARGIN, motion.lengthSqr()), 0, 1);
            double nearest = start.lerp(end, fraction).distanceTo(domain.position());
            double farthest = Math.max(start.distanceTo(domain.position()), end.distanceTo(domain.position()));
            if (nearest <= domain.radius() + radius && farthest >= domain.radius() - radius) {
                hit.add(domain.getUUID());
                domain.damageShell(damage, !domain.contains(start));
            }
        }
    }
}
