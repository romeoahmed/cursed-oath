package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Contact damage scales with armor and health; lattice targets resolve after excavation.
public final class CleaveContact {
    private static final double RANGE = 4;
    private static final float ARMOR_SCALING = 2, HEALTH_SCALING = 0.5f;

    private CleaveContact() {}

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    public static @Nullable Vec3 release(ServerPlayer player, TerrainDestruction.Work work) {
        var level = player.level();
        var hit = TechniqueCombat.contact(player, RANGE);
        var boundary = DomainInteractions.contact(
                player,
                player.getEyePosition(),
                hit == null ? player.getEyePosition().add(player.getLookAngle().scale(RANGE)) : hit.getLocation());
        if (boundary != null) {
            boundary.domain()
                    .damageShell(
                            TechniqueTuning.CLEAVE_DAMAGE, !boundary.domain().contains(player.getEyePosition()));
            work.close();
            return boundary.point();
        }
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            work.close();
            return null;
        }
        LivingEntity target =
                hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                        ? living
                        : null;
        if (target != null && TechniqueCombat.hasInfinity(target)) {
            work.close();
            return null;
        }
        var lattice = new CleaveLattice(hit.getLocation(), player.getLookAngle());
        if (!LoadedChunks.contains(player.level(), lattice.bounds())) {
            work.close();
            return null;
        }
        if (target != null) damage(player, target);
        if (work.finished() || !TechniqueCombat.isActive(player, level)) {
            work.close();
            return null;
        }
        work.cuts(lattice.cuts(), hit.getLocation());
        work.onComplete(() -> {
            if (!LoadedChunks.contains(level, lattice.bounds())) return;
            for (var neighbor : level.getEntitiesOfClass(LivingEntity.class, lattice.bounds())) {
                if (!TechniqueCombat.isActive(player, level)) return;
                if (neighbor != target && neighbor.level().equals(level) && exposed(player, lattice, neighbor))
                    damage(player, neighbor);
            }
        });
        return hit.getLocation();
    }

    private static boolean exposed(ServerPlayer player, CleaveLattice lattice, LivingEntity target) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return false;
        for (var cut : lattice.cuts()) {
            var contact = cut.contact(target.getBoundingBox());
            if (contact == null || DomainInteractions.contact(player, lattice.center(), contact) != null) continue;
            var hit = player.level()
                    .clipIncludingBorder(new ClipContext(
                            cut.source(contact), contact, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static void damage(ServerPlayer player, LivingEntity target) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return;
        target.hurtServer(player.level(), player.level().damageSources().playerAttack(player), damageAmount(target));
    }

    public static float damageAmount(LivingEntity target) {
        return Math.min(
                TechniqueTuning.CLEAVE_MAX_DAMAGE,
                TechniqueTuning.CLEAVE_DAMAGE
                        + target.getArmorValue() * ARMOR_SCALING
                        + target.getMaxHealth() * HEALTH_SCALING);
    }
}
