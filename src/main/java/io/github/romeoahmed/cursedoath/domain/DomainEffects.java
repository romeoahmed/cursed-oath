package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.technique.CleaveContact;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

final class DomainEffects {
    private DomainEffects() {}

    private static final ResourceKey<DamageType> OVERLOAD =
            ResourceKey.create(Registries.DAMAGE_TYPE, CursedOath.id("information_overload"));

    private static boolean suppressed(DomainEntity domain, LivingEntity target, Collection<DomainEntity> domains) {
        for (var other : domains)
            if (!other.equals(domain)
                    && !other.isRemoved()
                    && other.contains(target.getBoundingBox().getCenter())) return true;
        return false;
    }

    static boolean overloads(LivingEntity target) {
        if (!target.isAlive()) return false;
        var domains = DomainIndex.inLevel(target.level());
        for (var domain : domains)
            if (domain.closed()
                    && !domain.isRemoved()
                    && canHit(domain, target, domains)
                    && !DomainProtection.protects(target)) return true;
        return false;
    }

    private static boolean canHit(DomainEntity domain, LivingEntity target, Collection<DomainEntity> domains) {
        var owner = domain.caster;
        return owner != null
                && domain.contains(target.getBoundingBox().getCenter())
                && TechniqueCombat.canAffect(owner, target)
                && !(domain.closed() && owner.getBoundingBox().inflate(0.15).intersects(target.getBoundingBox()))
                && !suppressed(domain, target, domains);
    }

    static void tick(DomainEntity domain, List<DomainEntity> domains, Set<LivingEntity> affected) {
        var level = (ServerLevel) domain.level();
        var targets = level.getEntitiesOfClass(LivingEntity.class, domain.bounds());
        for (var target : targets) {
            // Damage callbacks can collapse the domain or invalidate its caster between targets.
            if (domain.isRemoved() || !domain.valid()) return;
            if (canHit(domain, target, domains) && !DomainProtection.protects(target, true))
                apply(domain, target, affected);
        }
        if (!domain.isRemoved() && domain.valid() && domain.technique() == Technique.MALEVOLENT_SHRINE)
            excavate(domain);
    }

    private static void apply(DomainEntity domain, LivingEntity target, Set<LivingEntity> affected) {
        long elapsed = domain.level().getGameTime() - domain.started();
        var level = (ServerLevel) domain.level();
        if (domain.closed()) {
            affected.add(target);
            if (elapsed > 0 && elapsed % 10 == 0)
                target.hurtServer(level, level.damageSources().source(OVERLOAD, domain, domain.caster), 40f);
        } else if (elapsed % 10 == 0)
            target.hurtServer(
                    level,
                    level.damageSources().indirectMagic(domain, domain.caster),
                    CleaveContact.damageAmount(target));
    }

    private static void excavate(DomainEntity domain) {
        var owner = domain.caster;
        if (owner == null) return;
        var work = domain.terrain;
        if (work == null || work.finished()) {
            work = TerrainDestruction.reserve(owner);
            domain.terrain = work;
        }
        if (work != null && work.ready() && !work.finished()) work.domainCuts(domain.position(), domain.radius());
    }

    static void erodeShells(DomainEntity shrine, List<DomainEntity> domains) {
        if (shrine.closed() || shrine.isRemoved() || (shrine.level().getGameTime() - shrine.started()) % 10 != 0)
            return;
        for (var barrier : domains) {
            if (!barrier.closed() || barrier.isRemoved()) continue;
            double distance = shrine.position().distanceTo(barrier.position());
            // An entirely enclosed open domain cannot strike the outside of a larger shell.
            if (distance < shrine.radius() + barrier.radius() && distance + shrine.radius() > barrier.radius())
                barrier.damageShell(8f, true);
        }
    }
}
