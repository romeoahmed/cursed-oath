package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// Attraction is advanced by its visible orb, using the same native entity clock.
final class BlueField {
    private static final double RADIUS = TechniqueTuning.BLUE_RADIUS,
            MIN_DISTANCE = 0.5,
            PULL = 0.42,
            CORE_RADIUS = TechniqueTuning.BLUE_CORE;
    private static final float CORE_DAMAGE = TechniqueTuning.BLUE_DAMAGE;
    private static final int DAMAGE_INTERVAL = TechniqueTuning.BLUE_INTERVAL;

    private BlueField() {}

    static void tick(ServerPlayer owner, Vec3 center, int age) {
        var bounds = AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2);
        boolean crush = (age - 1) % DAMAGE_INTERVAL == 0;
        for (var target : owner.level().getEntitiesOfClass(LivingEntity.class, bounds))
            pull(owner, target, center, crush);
    }

    private static void pull(ServerPlayer owner, LivingEntity target, Vec3 center, boolean crush) {
        if (!TechniqueCombat.canAffect(owner, target) || TechniqueCombat.hasInfinity(target)) return;
        var targetCenter = target.getBoundingBox().getCenter();
        if (DomainInteractions.contact(owner, center, targetCenter) != null) return;
        var delta = center.subtract(targetCenter);
        double distance = delta.length();
        if (distance > RADIUS || !LoadedChunks.contains(owner.level(), new AABB(center, targetCenter))) return;
        var obstruction = owner.level()
                .clip(new ClipContext(center, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (obstruction.getType() == HitResult.Type.MISS) {
            double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (distance >= MIN_DISTANCE) TechniqueCombat.push(target, delta.scale(PULL * (1 - resistance) / distance));
            if (crush && distance <= CORE_RADIUS)
                target.hurtServer(owner.level(), owner.level().damageSources().playerAttack(owner), CORE_DAMAGE);
        }
    }
}
