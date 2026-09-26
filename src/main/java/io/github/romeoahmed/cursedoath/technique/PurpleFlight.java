package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.world.LoadedChunks;
import io.github.romeoahmed.cursedoath.world.SweptVolume;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Flight owns contact; queued terrain work never determines speed or occlusion.
final class PurpleFlight {
    private static final Vec3 SIZE =
            new Vec3(TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_RADIUS);
    private static final double MOVEMENT_MARGIN = 4;
    private final TechniqueWave projectile;
    private final ServerPlayer caster;
    private final TerrainDestruction.Work terrain;
    private final ServerLevel level;
    private final Vec3 origin, direction;
    private final Set<UUID> hit = new HashSet<>();
    private double traveled;
    private final AABB clearance;

    PurpleFlight(TechniqueWave projectile, ServerPlayer caster, TerrainDestruction.Work terrain) {
        this.projectile = projectile;
        this.caster = caster;
        this.terrain = terrain;
        level = (ServerLevel) projectile.level();
        origin = caster.getEyePosition();
        direction = caster.getLookAngle();
        hit.add(caster.getUUID());
        clearance = caster.getBoundingBox().inflate(0.5, 1.0, 0.5);
    }

    void tick() {
        var start = traveled == 0 ? origin : projectile.position();
        double distance = Math.min(TechniqueTuning.PURPLE_SPEED, TechniqueTuning.PURPLE_RANGE - traveled);
        var end = projectile.position().add(direction.scale(distance));
        var volume = new SweptVolume(start, end, SIZE, true);
        if (!level.isPositionEntityTicking(BlockPos.containing(end))
                || !LoadedChunks.contains(level, volume.bounds())
                || !level.getWorldBorder().isWithinBounds(volume.bounds())) {
            projectile.discard();
            return;
        }
        DomainInteractions.pierce(
                level, caster, volume, TechniqueTuning.PURPLE_RADIUS, TechniqueTuning.PURPLE_DAMAGE, hit);
        for (var target :
                level.getEntitiesOfClass(LivingEntity.class, volume.bounds().inflate(MOVEMENT_MARGIN))) {
            if (!target.level().equals(level)
                    || hit.contains(target.getUUID())
                    || !TechniqueCombat.canAffect(caster, target)
                    || TechniqueCombat.hasInfinity(target)) continue;
            var movement = target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
            if (volume.entry(target.getBoundingBox(), movement) != null) {
                hit.add(target.getUUID());
                target.hurtServer(level, level.damageSources().playerAttack(caster), TechniqueTuning.PURPLE_DAMAGE);
                if (projectile.isRemoved()) return;
            }
        }
        if (!terrain.finished())
            terrain.pierce(volume, traveled == 0 ? null : new SweptVolume(origin, start, SIZE, true), clearance);
        traveled += distance;
        projectile.setPos(end);
        if (traveled >= TechniqueTuning.PURPLE_RANGE) projectile.discard();
    }

    void finish() {
        terrain.seal();
    }
}
