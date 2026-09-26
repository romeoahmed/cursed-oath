package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Damage and repulsion use the cover present at impact; excavation has its own budget.
final class RedBlast {
    private static final double RADIUS = TechniqueTuning.RED_RADIUS, KNOCKBACK = 4.5, LIFT = 0.8;
    private static final float DAMAGE = TechniqueTuning.RED_DAMAGE;
    // ServerExplosion offsets exposure samples toward +X/+Z by less than half a block.
    private static final double COVER_SAMPLE_MARGIN = 0.5;

    private RedBlast() {}

    static void impact(ServerPlayer player, Vec3 center, Vec3 direction) {
        var bounds = AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2);
        var level = player.level();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (!TechniqueCombat.isActive(player, level)) return;
            if (!target.level().equals(level)) continue;
            repel(player, target, center, direction);
        }
    }

    private static void repel(ServerPlayer player, LivingEntity target, Vec3 center, Vec3 direction) {
        if (!TechniqueCombat.canAffect(player, target) || TechniqueCombat.hasInfinity(target)) return;
        if (DomainInteractions.contact(player, center, target.getBoundingBox().getCenter()) != null) return;
        double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
        var coverBounds = target.getBoundingBox().expandTowards(COVER_SAMPLE_MARGIN, 0, COVER_SAMPLE_MARGIN);
        if (distance >= RADIUS || !LoadedChunks.contains(player.level(), new AABB(center, center).minmax(coverBounds)))
            return;
        double strength = (1 - distance / RADIUS) * ServerExplosion.getSeenPercent(center, target);
        if (strength <= 0) return;
        var level = player.level();
        var source = level.damageSources().playerAttack(player);
        if (target.hurtServer(level, source, (float) (DAMAGE * strength))
                && !target.isRemoved()
                && target.level().equals(level)) {
            // Native knockback is horizontal; this technique also works when aimed vertically.
            double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            TechniqueCombat.push(
                    target, direction.scale(KNOCKBACK).add(0, LIFT, 0).scale(strength * (1 - resistance)));
        }
    }
}
