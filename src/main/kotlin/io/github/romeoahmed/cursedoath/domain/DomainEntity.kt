package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.combat.Fighter
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** A tracked, unsaved anchor. Domain simulation runs once per server tick after all levels. */
class DomainEntity(
    type: EntityType<out DomainEntity>,
    level: Level,
) : Entity(type, level) {
    internal var fighter: Fighter? = null
    internal var caster: ServerPlayer? = null
    internal var terrain: TerrainDestruction.Work? = null
    val technique: Technique get() = Technique.fromWire(entityData.get(TECHNIQUE)) ?: Technique.UNLIMITED_VOID
    val radius: Double get() = entityData.get(RADIUS).toDouble()
    val started: Long get() = entityData.get(STARTED)
    val ownerId: Int get() = entityData.get(OWNER)
    val shell: Float get() = entityData.get(SHELL)
    val closed: Boolean get() = technique == Technique.UNLIMITED_VOID
    val bounds: AABB get() = AABB.ofSize(position(), radius * 2, radius * 2, radius * 2)

    internal val valid: Boolean get() {
        val owner = caster ?: return false
        val alive = owner.isAlive && !owner.isRemoved && !owner.isSpectator
        return alive && owner.level() === level() && level().gameTime - started < DURATION
    }

    internal fun configure(
        owner: ServerPlayer,
        ability: Technique,
        size: Double,
    ) {
        caster = owner
        setPos(owner.position())
        setYRot(owner.yRot)
        entityData.set(TECHNIQUE, ability.wireId)
        entityData.set(RADIUS, size.toFloat())
        entityData.set(STARTED, level().gameTime)
        entityData.set(OWNER, owner.id)
    }

    fun contains(point: Vec3): Boolean = DomainBoundary.contains(point, position(), radius)

    internal fun damageShell(
        amount: Float,
        outside: Boolean,
    ) {
        if (!closed || isRemoved) return
        entityData.set(SHELL, (shell - amount * if (outside) 1f else INTERNAL_RESISTANCE).coerceAtLeast(0f))
        if (shell <= 0f) Domains.end(this)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(TECHNIQUE, Technique.UNLIMITED_VOID.wireId)
        builder.define(RADIUS, VOID_RADIUS.toFloat())
        builder.define(STARTED, 0L)
        builder.define(OWNER, -1)
        builder.define(SHELL, SHELL_STRENGTH)
    }

    override fun onRemoval(reason: RemovalReason) {
        DomainIndex.remove(this)
        terrain?.close()
        if (!level().isClientSide) Domains.removed(this)
        super.onRemoval(reason)
    }

    override fun shouldRenderAtSqrDistance(distance: Double): Boolean =
        distance < (radius + VIEW_MARGIN) * (radius + VIEW_MARGIN)

    override fun hurtServer(
        level: ServerLevel,
        source: DamageSource,
        damage: Float,
    ) = false

    override fun readAdditionalSaveData(input: ValueInput) = Unit

    override fun addAdditionalSaveData(output: ValueOutput) = Unit

    companion object {
        private const val VIEW_MARGIN = 64.0
        const val VOID_RADIUS = 24.0
        const val DURATION = 600L
        const val BURNOUT = 200
        const val SHELL_STRENGTH = 100f
        private const val INTERNAL_RESISTANCE = 0.1f
        private val TECHNIQUE = SynchedEntityData.defineId(DomainEntity::class.java, EntityDataSerializers.INT)
        private val RADIUS = SynchedEntityData.defineId(DomainEntity::class.java, EntityDataSerializers.FLOAT)
        private val STARTED = SynchedEntityData.defineId(DomainEntity::class.java, EntityDataSerializers.LONG)
        private val OWNER = SynchedEntityData.defineId(DomainEntity::class.java, EntityDataSerializers.INT)
        private val SHELL = SynchedEntityData.defineId(DomainEntity::class.java, EntityDataSerializers.FLOAT)
    }
}
