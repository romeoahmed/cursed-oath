package io.github.romeoahmed.cursedoath.technique;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract sealed class TechniqueProjectile extends Projectile permits TechniqueOrb, TechniqueWave {
    static final EntityDataSerializer<Vec3> LAUNCH_POSITION_SERIALIZER =
            EntityDataSerializer.forValueType(Vec3.STREAM_CODEC);
    private static final EntityDataAccessor<Integer> TECHNIQUE =
            SynchedEntityData.defineId(TechniqueProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vec3> LAUNCH_POSITION =
            SynchedEntityData.defineId(TechniqueProjectile.class, LAUNCH_POSITION_SERIALIZER);

    protected TechniqueProjectile(EntityType<? extends TechniqueProjectile> type, Level level) {
        super(type, level);
    }

    public final Technique technique() {
        var value = Technique.fromWire(entityData.get(TECHNIQUE));
        return value == null ? Technique.PURPLE : value;
    }

    public final Vec3 launchPosition() {
        return entityData.get(LAUNCH_POSITION);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TECHNIQUE, Technique.PURPLE.wireId());
        builder.define(LAUNCH_POSITION, Vec3.ZERO);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return SteppedInterpolationHandler.create(this);
    }

    protected final void launch(ServerPlayer player, Technique ability) {
        setOwner(player);
        entityData.set(TECHNIQUE, ability.wireId());
        var eye = player.getEyePosition();
        var muzzle = eye.add(player.getLookAngle().scale(TechniqueTuning.launchDistance(ability)));
        entityData.set(LAUNCH_POSITION, muzzle);
        setPos(muzzle);
        setRot(player.getYRot(), player.getXRot());
    }
}
