package io.github.romeoahmed.cursedoath.technique;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public abstract sealed class TechniqueProjectile extends Projectile permits TechniqueOrb, TechniqueWave {
    private static final EntityDataAccessor<Integer> TECHNIQUE =
            SynchedEntityData.defineId(TechniqueProjectile.class, EntityDataSerializers.INT);

    protected TechniqueProjectile(EntityType<? extends TechniqueProjectile> type, Level level) {
        super(type, level);
    }

    public final Technique technique() {
        var value = Technique.fromWire(entityData.get(TECHNIQUE));
        return value == null ? Technique.PURPLE : value;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TECHNIQUE, Technique.PURPLE.wireId());
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return SteppedInterpolationHandler.create(this);
    }

    protected final void launch(ServerPlayer player, Technique ability) {
        setOwner(player);
        entityData.set(TECHNIQUE, ability.wireId());
        setPos(player.getEyePosition());
        setRot(player.getYRot(), player.getXRot());
    }
}
