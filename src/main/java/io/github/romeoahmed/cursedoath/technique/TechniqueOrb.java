package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.network.TechniqueEvent;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Blue carries attraction; contact or range expiry settles Blue and detonates Red.
public final class TechniqueOrb extends TechniqueProjectile {
    private static final double BLUE_RANGE = 18,
            RED_RANGE = 24,
            BLUE_SPEED = 0.9,
            RED_SPEED = 1.5,
            SURFACE_OFFSET = 0.01;
    private static final float AIM_MARGIN = 0.35f, IMPACT_VOLUME = 2, IMPACT_PITCH = 0.65f;
    private TerrainDestruction.@Nullable Work excavation;
    private double remaining;
    private boolean settled;

    public TechniqueOrb(EntityType<? extends TechniqueOrb> type, Level level) {
        super(type, level);
    }

    public void configure(ServerPlayer player, Technique ability, TerrainDestruction.Work work) {
        launch(player, ability);
        excavation = work;
        remaining = ability == Technique.BLUE ? BLUE_RANGE : RED_RANGE;
        setDeltaMovement(player.getLookAngle().scale(ability == Technique.BLUE ? BLUE_SPEED : RED_SPEED));
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (excavation != null) excavation.close();
        super.onRemoval(reason);
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        var work = excavation;
        if (work == null) {
            discard();
            return;
        }
        var player = work.owner();
        if (!player.isAlive()
                || player.isRemoved()
                || player.isSpectator()
                || player.level() != level
                || tickCount > TechniqueTuning.BLUE_DURATION) {
            discard();
            return;
        }
        if (!settled && work.finished()) {
            discard();
            return;
        }
        if (!settled) travel(level, player, work);
        if (!isRemoved() && technique() == Technique.BLUE) BlueField.tick(player, position(), tickCount);
    }

    private void travel(ServerLevel level, ServerPlayer player, TerrainDestruction.Work work) {
        var movement = getDeltaMovement();
        double distance = Math.min(movement.length(), remaining);
        var direction = movement.normalize();
        var end = position().add(direction.scale(distance));
        if (!level.isPositionEntityTicking(BlockPos.containing(end))
                || !LoadedChunks.contains(level, new AABB(position(), end).inflate(AIM_MARGIN))) {
            discard();
            return;
        }
        var block = level.clipIncludingBorder(
                new ClipContext(position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        HitResult hit = block;
        double nearest = Double.POSITIVE_INFINITY;
        for (var candidate : ProjectileUtil.getManyEntityHitResult(
                level,
                this,
                position(),
                block.getLocation(),
                new AABB(position(), block.getLocation()).inflate(AIM_MARGIN),
                entity -> entity instanceof LivingEntity living && TechniqueCombat.canAffect(player, living),
                AIM_MARGIN,
                ClipContext.Block.COLLIDER,
                true,
                true)) {
            double separation = candidate.getLocation().distanceToSqr(position());
            if (separation < nearest) {
                hit = candidate;
                nearest = separation;
            }
        }
        var barrier = DomainInteractions.contact(player, position(), hit.getLocation());
        if (barrier != null) {
            float power = technique() == Technique.BLUE ? TechniqueTuning.BLUE_OUTPUT : TechniqueTuning.RED_DAMAGE;
            barrier.domain().damageShell(power, !barrier.domain().contains(position()));
            setPos(barrier.point().subtract(direction.scale(SURFACE_OFFSET)));
            impact(player, work, direction);
            return;
        }
        var center = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(direction.scale(SURFACE_OFFSET))
                : hit.getLocation();
        setPos(center);
        remaining -= distance;
        if (hit.getType() != HitResult.Type.MISS || remaining <= 0) impact(player, work, direction);
    }

    private void impact(ServerPlayer player, TerrainDestruction.Work work, Vec3 direction) {
        settled = true;
        setDeltaMovement(Vec3.ZERO);
        if (technique() == Technique.BLUE) work.sphere(position(), TechniqueTuning.BLUE_EXCAVATION);
        else {
            RedBlast.impact(player, position(), direction);
            if (isRemoved() || work.finished()) {
                discard();
                return;
            }
            work.sphere(position(), TechniqueTuning.RED_EXCAVATION);
            TechniqueCombat.event(
                    player,
                    new TechniqueEvent(
                            level().dimension().identifier(),
                            getUUID(),
                            player.getId(),
                            technique().wireId(),
                            TechniqueEvent.IMPACT,
                            level().getGameTime(),
                            position().subtract(direction),
                            position()));
            level().playSound(
                            null,
                            getX(),
                            getY(),
                            getZ(),
                            SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.PLAYERS,
                            IMPACT_VOLUME,
                            IMPACT_PITCH);
            // Keep queued excavation alive after the projectile is discarded.
            excavation = null;
            discard();
        }
    }
}
