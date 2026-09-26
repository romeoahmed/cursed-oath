package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import io.github.romeoahmed.cursedoath.world.SweptVolume;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Server-swept volume; native tracking supplies spawn, interpolation and removal.
public final class TechniqueWave extends TechniqueProjectile {
    private static final double SLASH_RANGE = TechniqueTuning.DISMANTLE_RANGE,
            SLASH_SPEED = 6,
            MOVEMENT_MARGIN = 4,
            CONTACT_STEP = 0.001;
    private static final float SLASH_DAMAGE = TechniqueTuning.DISMANTLE_DAMAGE;
    private static final int MAX_AGE = 480;
    private static final Vec3 SIZE = new Vec3(TechniqueTuning.DISMANTLE_WIDTH, 0.18, 0.18);
    private TerrainDestruction.@Nullable Work excavation;
    private @Nullable PurpleFlight purple;
    private Vec3 direction = Vec3.ZERO, origin = Vec3.ZERO;
    private @Nullable Vec3 next;
    private boolean shellContact;
    private double traveled;
    private final Set<UUID> hit = new HashSet<>();

    public TechniqueWave(EntityType<? extends TechniqueWave> type, Level level) {
        super(type, level);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (purple != null) purple.finish();
        else if (excavation != null) excavation.close();
        super.onRemoval(reason);
    }

    public void configure(ServerPlayer player, Technique ability, TerrainDestruction.Work work) {
        launch(player, ability);
        if (ability == Technique.PURPLE) {
            purple = new PurpleFlight(this, player, work);
            return;
        }
        direction = player.getLookAngle();
        origin = position();
        excavation = work;
        work.persistent(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        var flight = purple;
        if (flight != null) flight.tick();
        else tickSlash();
    }

    private void tickSlash() {
        if (!(level() instanceof ServerLevel level)) return;
        var work = excavation;
        if (work == null) {
            discard();
            return;
        }
        var player = work.owner();
        if (!TechniqueCombat.isActive(player, level) || work.finished() || tickCount >= MAX_AGE) {
            discard();
            return;
        }
        var end = next == null ? position().add(direction.scale(CONTACT_STEP)) : next;
        var bounds = new SweptVolume(origin, end, SIZE).bounds().inflate(MOVEMENT_MARGIN);
        if (!LoadedChunks.contains(level, bounds)) {
            discard();
            return;
        }
        if (work.ready()) advance(level, player, work);
        else damage(level, player, new SweptVolume(position(), position().add(direction.scale(CONTACT_STEP)), SIZE));
    }

    private void advance(ServerLevel level, ServerPlayer player, TerrainDestruction.Work work) {
        var destination = next;
        if (destination != null) {
            damage(level, player, new SweptVolume(position(), destination, SIZE));
            if (isRemoved() || !TechniqueCombat.isActive(player, level)) {
                discard();
                return;
            }
            var obstruction = level.clipIncludingBorder(
                    new ClipContext(position(), destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (obstruction.getType() != HitResult.Type.MISS) {
                setPos(obstruction.getLocation());
                discard();
                return;
            }
            traveled += destination.distanceTo(position());
            setPos(destination);
            next = null;
            if (shellContact) discard();
        }
        if (!isRemoved()) schedule(level, work);
    }

    private void schedule(ServerLevel level, TerrainDestruction.Work work) {
        if (traveled >= SLASH_RANGE) {
            discard();
            return;
        }
        var end = position().add(direction.scale(Math.min(SLASH_SPEED, SLASH_RANGE - traveled)));
        if (!level.isPositionEntityTicking(BlockPos.containing(end))
                || !LoadedChunks.contains(level, new SweptVolume(origin, end, SIZE).bounds())) {
            discard();
            return;
        }
        var barrier = DomainInteractions.contact(work.owner(), position(), end);
        var destination = barrier == null ? end : barrier.point();
        shellContact = barrier != null;
        if (barrier != null)
            barrier.domain().damageShell(SLASH_DAMAGE, !barrier.domain().contains(position()));
        next = destination;
        work.cuts(List.of(new SweptVolume(position(), destination, SIZE)), origin);
    }

    private void damage(ServerLevel level, ServerPlayer player, SweptVolume volume) {
        // Broad phase includes ordinary target movement; narrow phase uses a relative segment.
        for (var target :
                level.getEntitiesOfClass(LivingEntity.class, volume.bounds().inflate(MOVEMENT_MARGIN))) {
            if (!TechniqueCombat.isActive(player, level)) return;
            if (!target.level().equals(level)) continue;
            hitTarget(player, target, volume);
            // Damage callbacks can remove this wave and close its terrain reservation.
            if (isRemoved()) return;
        }
    }

    private void hitTarget(ServerPlayer player, LivingEntity target, SweptVolume volume) {
        if (!TechniqueCombat.canAffect(player, target)
                || TechniqueCombat.hasInfinity(target)
                || hit.contains(target.getUUID())) return;
        var movement = target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
        var contact = volume.contact(target.getBoundingBox(), movement);
        if (contact == null) return;
        var source = volume.source(contact, origin);
        var level = player.level();
        var covered = level.clipIncludingBorder(
                new ClipContext(source, contact, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (covered.getType() != HitResult.Type.MISS || DomainInteractions.contact(player, source, contact) != null)
            return;
        hit.add(target.getUUID());
        target.hurtServer(level, level.damageSources().playerAttack(player), SLASH_DAMAGE);
    }
}
