package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object Domains {
    private const val OPEN_PITCH = 0.65f
    private const val CLOSE_PITCH = 0.6f
    val TYPE: EntityType<DomainEntity> =
        EntityType.Builder
            .of(::DomainEntity, MobCategory.MISC)
            .sized(1f, 1f)
            .fireImmune()
            .noSave()
            .noSummon()
            .clientTrackingRange(32)
            .updateInterval(20)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, CursedOath.id("domain")))
    private val OVERLOADED =
        AttachmentRegistry.create(CursedOath.id("overloaded")) {
            it.initializer { false }.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
        }
    private var controlled = emptySet<LivingEntity>()

    fun initialize() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CursedOath.id("domain"), TYPE)
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> if (entity is DomainEntity) DomainIndex.add(entity) }
        ServerTickEvents.END_SERVER_TICK.register { tick() }
        ServerLifecycleEvents.SERVER_STOPPED.register { clear() }
        DomainSounds.initialize()
        DomainInteractions.initialize()
        BarrierState.initialize()
    }

    fun inLevel(level: Level): List<DomainEntity> = DomainIndex.inLevel(level).filterNot { it.isRemoved }

    fun isOverloaded(entity: Entity): Boolean = entity.getAttached(OVERLOADED) == true

    internal fun ownedBy(player: ServerPlayer): DomainEntity? =
        DomainIndex.inLevel(player.level()).firstOrNull { !it.isRemoved && it.caster === player }

    internal fun open(
        player: ServerPlayer,
        technique: Technique,
    ): DomainEntity {
        ownedBy(player)?.let(::end)
        val radius =
            if (technique == Technique.UNLIMITED_VOID) {
                DomainEntity.VOID_RADIUS
            } else {
                player.getAttachedOrCreate(SorcererData.PROFILE).domainRadius
            }
        val domain = DomainEntity(TYPE, player.level())
        domain.configure(player, technique, radius)
        domain.fighter = CombatRuntime.fighter(player)
        if (player.level().addFreshEntity(domain)) {
            domain.fighter?.imposeBurnout(DomainEntity.BURNOUT)
            sound(domain, DomainSounds.OPEN, OPEN_PITCH)
        }
        return domain
    }

    internal fun end(domain: DomainEntity) {
        if (domain.isRemoved) return
        domain.terrain?.close()
        sound(domain, DomainSounds.CLOSE, CLOSE_PITCH)
        domain.discard()
    }

    internal fun removed(domain: DomainEntity) {
        domain.fighter?.imposeBurnout(DomainEntity.BURNOUT)
        // Recompute aggregate control immediately when an anchor disappears during a hit callback.
        for (entity in controlled) {
            if (!DomainEffects.overloads(entity)) {
                entity.setAttached(OVERLOADED, false)
            }
        }
    }

    internal fun cancel(player: ServerPlayer) {
        ownedBy(player)?.let(::end)
    }

    internal fun tick() {
        val all = DomainIndex.all
        if (all.isEmpty() && controlled.isEmpty()) return
        val affected = mutableSetOf<LivingEntity>()
        for (domain in all) {
            if (!domain.valid) {
                end(domain)
            }
        }
        for (group in all.filterNot { it.isRemoved }.groupBy { it.level() }.values) {
            for (domain in group) DomainEffects.erodeShells(domain, group)
            for (domain in group) {
                if (domain.isRemoved) continue
                DomainEffects.tick(domain, group, affected)
            }
        }
        affected.removeAll { !DomainEffects.overloads(it) }
        for (entity in controlled) {
            if (entity !in affected) entity.setAttached(OVERLOADED, false)
        }
        for (entity in affected) {
            if (!isOverloaded(entity)) entity.setAttached(OVERLOADED, true)
            entity.stopUsingItem()
            entity.deltaMovement = Vec3.ZERO
        }
        controlled = affected
        DomainProtection.finishTick()
    }

    internal fun clear() {
        for (domain in DomainIndex.all) {
            domain.terrain?.close()
            domain.discard()
        }
        for (entity in controlled) entity.setAttached(OVERLOADED, false)
        controlled = emptySet()
    }

    private fun sound(
        domain: DomainEntity,
        sound: SoundEvent,
        pitch: Float,
    ) {
        val level = domain.level() as? ServerLevel ?: return
        level.playSound(null, domain.x, domain.y, domain.z, sound, SoundSource.PLAYERS, 2f, pitch)
    }
}
