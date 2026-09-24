package io.github.romeoahmed.cursedoath.technique

import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.InterpolationHandler
import net.minecraft.world.entity.SteppedInterpolationHandler
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.Level

/** Native entity tracking is the single source of position, technique and lifetime for rendering. */
sealed class TechniqueProjectile(
    type: EntityType<out TechniqueProjectile>,
    level: Level,
) : Projectile(type, level) {
    var technique: Technique
        get() = Technique.fromWire(entityData.get(TECHNIQUE)) ?: Technique.PURPLE
        protected set(value) {
            entityData.set(TECHNIQUE, value.wireId)
        }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(TECHNIQUE, Technique.PURPLE.wireId)
    }

    override fun createInterpolationHandler(): InterpolationHandler = SteppedInterpolationHandler.create(this)

    protected fun launch(
        player: ServerPlayer,
        ability: Technique,
    ) {
        setOwner(player)
        technique = ability
        setPos(player.eyePosition)
        setRot(player.yRot, player.xRot)
    }

    private companion object {
        val TECHNIQUE = SynchedEntityData.defineId(TechniqueProjectile::class.java, EntityDataSerializers.INT)
    }
}
