package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.combat.Fighter;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A tracked, unsaved anchor. Domain simulation runs once per server tick after all levels.
public final class DomainEntity extends Entity {
    public static final double VOID_RADIUS = 24;
    public static final long DURATION = 600;
    public static final int BURNOUT = 200;
    public static final float SHELL_STRENGTH = 100;
    private static final EntityDataAccessor<Integer> TECHNIQUE =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> STARTED =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SHELL =
            SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    Fighter fighter;

    @Nullable
    ServerPlayer caster;

    TerrainDestruction.@Nullable Work terrain;
    double ground = Double.NEGATIVE_INFINITY;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
    }

    public Technique technique() {
        var value = Technique.fromWire(entityData.get(TECHNIQUE));
        return value == null ? Technique.UNLIMITED_VOID : value;
    }

    public double radius() {
        return entityData.get(RADIUS);
    }

    public long started() {
        return entityData.get(STARTED);
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public float shell() {
        return entityData.get(SHELL);
    }

    public boolean closed() {
        return technique() == Technique.UNLIMITED_VOID;
    }

    public AABB bounds() {
        double diameter = radius() * 2;
        return AABB.ofSize(position(), diameter, diameter, diameter);
    }

    @SuppressWarnings("ReferenceEquality") // Ownership follows live instances, not entity IDs.
    boolean valid() {
        var owner = caster;
        return owner != null
                && owner.isAlive()
                && !owner.isRemoved()
                && !owner.isSpectator()
                && owner.level() == level()
                && level().getGameTime() - started() < DURATION;
    }

    void configure(ServerPlayer owner, Technique ability, double size) {
        caster = owner;
        // Snapshot actual block support, including slabs; jumping later cannot move the excavation floor.
        var feet = owner.getBoundingBox();
        var support = new AABB(feet.minX, feet.minY - 0.06, feet.minZ, feet.maxX, feet.minY, feet.maxZ);
        if (owner.level().getBlockCollisions(owner, support).iterator().hasNext()) ground = owner.getY();
        setPos(owner.position());
        setYRot(owner.getYRot());
        entityData.set(TECHNIQUE, ability.wireId());
        entityData.set(RADIUS, (float) size);
        entityData.set(STARTED, level().getGameTime());
        entityData.set(OWNER, owner.getId());
    }

    public boolean contains(Vec3 point) {
        return DomainBoundary.contains(point, position(), radius());
    }

    public void damageShell(float amount, boolean outside) {
        if (!closed() || isRemoved()) return;
        entityData.set(SHELL, Math.max(shell() - amount * (outside ? 1f : 0.1f), 0f));
        if (shell() <= 0) Domains.end(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TECHNIQUE, Technique.UNLIMITED_VOID.wireId());
        builder.define(RADIUS, (float) VOID_RADIUS);
        builder.define(STARTED, 0L);
        builder.define(OWNER, -1);
        builder.define(SHELL, SHELL_STRENGTH);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        DomainIndex.remove(this);
        if (terrain != null) terrain.close();
        if (!level().isClientSide()) Domains.removed(this);
        super.onRemoval(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < (radius() + 64) * (radius() + 64);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
