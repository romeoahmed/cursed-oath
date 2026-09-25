package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.technique.CleaveContact
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity

internal object DomainEffects {
    private const val CONTACT_MARGIN = 0.15
    private const val HIT_INTERVAL = 10L
    private const val OVERLOAD_DAMAGE = 40f
    private val OVERLOAD = ResourceKey.create(Registries.DAMAGE_TYPE, CursedOath.id("information_overload"))
    private const val SHELL_DAMAGE = 8f

    private fun suppressed(
        domain: DomainEntity,
        target: LivingEntity,
        domains: Collection<DomainEntity>,
    ): Boolean = domains.any { it !== domain && !it.isRemoved && it.contains(target.boundingBox.center) }

    fun overloads(target: LivingEntity): Boolean {
        if (!target.isAlive) return false
        val domains = DomainIndex.inLevel(target.level())
        return domains.any { overloads(it, target, domains) }
    }

    private fun overloads(
        domain: DomainEntity,
        target: LivingEntity,
        domains: Collection<DomainEntity>,
    ): Boolean =
        domain.closed && !domain.isRemoved && canHit(domain, target, domains) && !DomainProtection.protects(target)

    private fun canHit(
        domain: DomainEntity,
        target: LivingEntity,
        domains: Collection<DomainEntity>,
    ): Boolean {
        val owner = domain.caster ?: return false
        return domain.contains(target.boundingBox.center) && TechniqueCombat.canAffect(owner, target) &&
            !(domain.closed && owner.boundingBox.inflate(CONTACT_MARGIN).intersects(target.boundingBox)) &&
            !suppressed(domain, target, domains)
    }

    fun tick(
        domain: DomainEntity,
        domains: List<DomainEntity>,
        affected: MutableSet<LivingEntity>,
    ) {
        val level = domain.level() as ServerLevel
        val targets = level.getEntitiesOfClass(LivingEntity::class.java, domain.bounds)
        for (target in targets) {
            // A damage callback can collapse this domain or invalidate its caster between targets.
            if (domain.isRemoved || !domain.valid) return
            if (canHit(domain, target, domains) && !DomainProtection.protects(target, erode = true)) {
                apply(domain, target, affected)
            }
        }
        if (!domain.isRemoved && domain.valid && domain.technique == Technique.MALEVOLENT_SHRINE) {
            excavate(domain)
        }
    }

    private fun apply(
        domain: DomainEntity,
        target: LivingEntity,
        affected: MutableSet<LivingEntity>,
    ) {
        val elapsed = domain.level().gameTime - domain.started
        if (domain.closed) {
            affected.add(target)
            if (elapsed > 0 && elapsed % HIT_INTERVAL == 0L) {
                val level = domain.level() as ServerLevel
                target.hurtServer(
                    level,
                    level.damageSources().source(OVERLOAD, domain, domain.caster),
                    OVERLOAD_DAMAGE,
                )
            }
        } else if (elapsed % HIT_INTERVAL == 0L) {
            val level = domain.level() as ServerLevel
            target.hurtServer(
                level,
                level.damageSources().indirectMagic(domain, domain.caster),
                CleaveContact.damageAmount(target),
            )
        }
    }

    private fun excavate(domain: DomainEntity) {
        val owner = domain.caster ?: return
        if (domain.terrain?.finished != false) domain.terrain = TerrainDestruction.reserve(owner)
        domain.terrain?.let { work ->
            if (work.ready && !work.finished) work.domainCuts(domain.position(), domain.radius)
        }
    }

    fun erodeShells(
        shrine: DomainEntity,
        domains: List<DomainEntity>,
    ) {
        if (shrine.closed || shrine.isRemoved) return
        if ((shrine.level().gameTime - shrine.started) % HIT_INTERVAL != 0L) return
        for (barrier in domains) {
            if (!barrier.closed || barrier.isRemoved) continue
            val distance = shrine.position().distanceTo(barrier.position())
            // An entirely enclosed open domain cannot strike the outside of a larger shell.
            if (distance < shrine.radius + barrier.radius && distance + shrine.radius > barrier.radius) {
                barrier.damageShell(SHELL_DAMAGE, true)
            }
        }
    }
}
