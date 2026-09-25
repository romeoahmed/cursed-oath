package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.world.SweptVolume
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import java.util.UUID

object DomainInteractions {
    private const val CONTACT_MARGIN = 0.01
    private const val PROJECTILE_POWER = 4f
    private const val MIN_ATTACK = 2f
    private const val ATTACK_PARTIAL_TICK = 0.5f

    fun initialize() {
        PlayerBlockBreakEvents.BEFORE.register { _, player, _, _, _ -> !Domains.isOverloaded(player) }
        AttackEntityCallback.EVENT.register { player, _, _, target, _ ->
            if (contact(player, player.eyePosition, target.boundingBox.center) != null) {
                InteractionResult.FAIL
            } else {
                blocked(player)
            }
        }
        AttackBlockCallback.EVENT.register { player, _, _, _, _ -> blocked(player) }
        UseEntityCallback.EVENT.register { player, _, _, _, _ -> blocked(player) }
        UseBlockCallback.EVENT.register { player, _, _, _ -> blocked(player) }
        UseItemCallback.EVENT.register { player, _, _ -> blocked(player) }
    }

    private fun blocked(entity: Entity): InteractionResult =
        if (Domains.isOverloaded(entity)) InteractionResult.FAIL else InteractionResult.PASS

    /** Clips native movement against closed shells on both client and server. */
    fun movement(
        entity: Entity,
        requested: Vec3,
    ): Vec3 {
        if (Domains.isOverloaded(entity)) return Vec3.ZERO
        if (entity is DomainEntity || entity.isSpectator || requested.lengthSqr() == 0.0) return requested
        if (entity is Player && entity.isCreative) return requested
        val domains = DomainIndex.inLevel(entity.level())
        if (domains.isEmpty()) return requested
        var result = requested
        val start = entity.boundingBox.center
        for (domain in domains) {
            if (domain.isRemoved || !domain.closed || exempt(entity, domain)) continue
            val crossing = DomainBoundary.crossing(start, start.add(result), domain.position(), domain.radius)
            if (crossing != null) {
                val distance = result.length()
                result = result.scale((crossing - CONTACT_MARGIN / distance).coerceAtLeast(0.0))
            }
        }
        return result
    }

    private fun exempt(
        entity: Entity,
        domain: DomainEntity,
    ): Boolean {
        if (entity.id == domain.ownerId) return true
        val owner = domain.level().getEntity(domain.ownerId) as? Player ?: return false
        return entity is Player && (!owner.canHarmPlayer(entity) || owner.isAlliedTo(entity))
    }

    fun projectile(projectile: Projectile): Boolean {
        if (projectile.level().isClientSide) return false
        val motion = projectile.deltaMovement
        val start = projectile.position()
        val hit = contact(projectile.owner ?: projectile, start, start.add(motion)) ?: return false
        val obstruction =
            projectile.level().clipIncludingBorder(
                ClipContext(
                    start,
                    hit.point,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    projectile,
                ),
            )
        if (obstruction.location.distanceToSqr(start) < hit.point.distanceToSqr(start)) return false
        hit.domain.damageShell(
            maxOf(MIN_ATTACK, motion.length().toFloat() * PROJECTILE_POWER),
            !hit.domain.contains(start),
        )
        projectile.discard()
        return true
    }

    /** Uses native reach and attack strength to resolve an empty swing against the shell. */
    fun punch(player: ServerPlayer) {
        if (Domains.isOverloaded(player) || player.isSpectator) return
        if (DomainIndex.inLevel(player.level()).none { !it.isRemoved && it.closed }) return
        val reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
        strike(
            player,
            player.eyePosition,
            player
                .level()
                .clipIncludingBorder(
                    ClipContext(
                        player.eyePosition,
                        player.eyePosition.add(player.lookAngle.scale(reach)),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        player,
                    ),
                ).location,
            player.getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat().coerceAtLeast(MIN_ATTACK) *
                player.getAttackStrengthScale(ATTACK_PARTIAL_TICK),
        )
    }

    fun strike(
        owner: Entity,
        start: Vec3,
        end: Vec3,
        damage: Float,
    ): Boolean {
        val hit = contact(owner, start, end) ?: return false
        hit.domain.damageShell(damage, !hit.domain.contains(start))
        return true
    }

    data class Contact(
        val domain: DomainEntity,
        val point: Vec3,
    )

    fun contact(
        owner: Entity,
        start: Vec3,
        end: Vec3,
    ): Contact? {
        var nearest: DomainEntity? = null
        var fraction = Double.POSITIVE_INFINITY
        for (domain in DomainIndex.inLevel(owner.level())) {
            if (domain.isRemoved || !domain.closed || exempt(owner, domain)) continue
            val crossing = DomainBoundary.crossing(start, end, domain.position(), domain.radius)
            if (crossing != null && crossing < fraction) {
                nearest = domain
                fraction = crossing
            }
        }
        return nearest?.let { Contact(it, start.lerp(end, fraction)) }
    }

    /** A traveling volume can graze a shell even when its center line misses it. */
    internal fun pierce(
        owner: Entity,
        volume: SweptVolume,
        radius: Double,
        damage: Float,
        hit: MutableSet<UUID>,
    ) {
        val start = volume.start
        val end = volume.end
        val motion = end.subtract(start)
        for (domain in Domains.inLevel(owner.level())) {
            if (!domain.closed || exempt(owner, domain) || domain.uuid in hit) continue
            val fraction =
                (
                    domain.position().subtract(start).dot(motion) /
                        motion.lengthSqr().coerceAtLeast(CONTACT_MARGIN)
                ).coerceIn(0.0, 1.0)
            val nearest = start.lerp(end, fraction).distanceTo(domain.position())
            val farthest = maxOf(start.distanceTo(domain.position()), end.distanceTo(domain.position()))
            if (nearest <= domain.radius + radius && farthest >= domain.radius - radius) {
                hit.add(domain.uuid)
                domain.damageShell(damage, !domain.contains(start))
            }
        }
    }
}
