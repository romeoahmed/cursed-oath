package io.github.romeoahmed.cursedoath.domain;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.Defense;
import io.github.romeoahmed.cursedoath.combat.SorcererAttachments;
import io.github.romeoahmed.cursedoath.combat.SorcererProfile;
import io.github.romeoahmed.cursedoath.technique.InfinityDefense;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DomainGameTest {
    private static final BlockPos WALL = new BlockPos(2, 2, 4);

    @GameTest(environment = "cursed-oath-test:domain_terrain", structure = "cursed-oath-test:arena", maxTicks = 160)
    public void groundedShrinePreservesSlabSupportAndFoundationAfterCasterMoves(GameTestHelper helper) {
        var caster = caster(helper);
        var slab = new BlockPos(14, 10, 14);
        var foundation = new BlockPos(14, 9, 15);
        var wall = new BlockPos(14, 12, 15);
        helper.setBlock(slab, Blocks.STONE_SLAB);
        helper.setBlock(foundation, Blocks.STONE);
        helper.setBlock(wall, Blocks.STONE);
        caster.setPos(helper.absoluteVec(new Vec3(14.5, 10.5, 14.5)));
        caster.setAttached(SorcererAttachments.PROFILE, new SorcererProfile().withDomainRadius(16));
        Domains.open(caster, Technique.MALEVOLENT_SHRINE);
        caster.setPos(caster.position().add(0, 5, 0));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, wall);
            helper.assertBlockPresent(Blocks.STONE_SLAB, slab);
            helper.assertBlockPresent(Blocks.STONE, foundation);
        });
    }

    @GameTest(environment = "cursed-oath-test:domain_terrain", structure = "cursed-oath-test:arena", maxTicks = 160)
    public void airborneShrineCutsBelowButLeavesTheSphericalCorners(GameTestHelper helper) {
        var caster = caster(helper);
        caster.setPos(helper.absoluteVec(new Vec3(14.5, 16, 14.5)));
        caster.setAttached(SorcererAttachments.PROFILE, new SorcererProfile().withDomainRadius(16));
        var below = new BlockPos(14, 8, 14);
        var corner = new BlockPos(29, 29, 29);
        helper.setBlock(below, Blocks.STONE);
        helper.setBlock(corner, Blocks.STONE);
        var inside = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(14, 23, 14));
        var outside = stationaryTarget(helper, EntityTypes.VILLAGER, corner.above());
        Domains.open(caster, Technique.MALEVOLENT_SHRINE);
        Domains.tick();
        helper.assertTrue(inside.getHealth() < inside.getMaxHealth(), "Sure hits extend vertically inside the sphere");
        helper.assertTrue(outside.getHealth() == outside.getMaxHealth(), "Sphere corners cannot receive sure hits");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, below);
            helper.assertBlockPresent(Blocks.STONE, corner);
        });
    }

    @GameTest(environment = "cursed-oath-test:domains")
    @SuppressWarnings("ReferenceEquality") // Verify live entity ownership, not equality by entity ID.
    public void collapseDuringSureHitStopsRemainingTargetsAndTerrain(GameTestHelper helper) {
        var caster = caster(helper);
        var targets = List.of(
                stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6)),
                stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(3, 1, 6)));
        var domain = Domains.open(caster, Technique.MALEVOLENT_SHRINE);
        allowDamage(helper, targets, (target, source, amount) -> {
            if (source.getDirectEntity() == domain) Domains.end(domain);
            return true;
        });
        Domains.tick();
        helper.assertTrue(domain.isRemoved(), "The damage callback must collapse the anchor");
        helper.assertTrue(
                targets.stream()
                                .filter(target -> target.getHealth() < target.getMaxHealth())
                                .count()
                        == 1,
                "Collapse must stop remaining sure hits: "
                        + targets.stream().map(LivingEntity::getHealth).toList());
        helper.assertTrue(domain.terrain == null, "A removed anchor must not reserve more excavation");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    @SuppressWarnings("ReferenceEquality") // Verify live entity ownership, not equality by entity ID.
    public void damageCallbackMovingAnotherTargetOutsideStopsItsSureHit(GameTestHelper helper) {
        var caster = caster(helper);
        var targets = List.of(
                stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6)),
                stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(3, 1, 6)));
        var domain = Domains.open(caster, Technique.MALEVOLENT_SHRINE);
        var relocated = new AtomicBoolean();
        allowDamage(helper, targets, (target, source, amount) -> {
            if (!relocated.get() && source.getDirectEntity() == domain) {
                relocated.set(true);
                for (var other : targets)
                    if (other != target) other.setPos(domain.position().add(domain.radius() * 2, 0.0, 0.0));
            }
            return true;
        });
        Domains.tick();
        helper.assertTrue(relocated.get(), "The first hit must invoke the relocation callback");
        helper.assertTrue(
                targets.stream()
                                .filter(target -> target.getHealth() < target.getMaxHealth())
                                .count()
                        == 1,
                "Recheck targets after damage callbacks");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void contactExemptionDoesNotErodeSimpleDomain(GameTestHelper helper) {
        var caster = caster(helper);
        var defender = caster(helper);
        CombatRuntime.practice(defender, true);
        var fighter = CombatRuntime.fighter(defender);
        fighter.prepare(Technique.SIMPLE_DOMAIN);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 1));
        target.setPos(caster.position());
        Domains.open(caster, Technique.UNLIMITED_VOID);
        try {
            Domains.tick();
            helper.assertTrue(!Domains.isOverloaded(target), "Contact exempts the target");
            helper.assertTrue(
                    fighter.defense().simple() == Defense.SIMPLE_STRENGTH, "An exempt target exerts no pressure");
            target.setPos(caster.position().add(0.0, 0.0, 2.0));
            Domains.tick();
            helper.assertTrue(
                    fighter.defense().simple() == Defense.SIMPLE_STRENGTH - 1, "Breaking contact resumes pressure");
        } finally {
            CombatRuntime.practice(defender, false);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void voidControlsNativeMovementAndReleasesOnCollapse(GameTestHelper helper) {
        var caster = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        Domains.tick();
        helper.assertTrue(Domains.isOverloaded(target), "Void must apply its own control state");
        var position = target.position();
        target.move(MoverType.SELF, new Vec3(1.0, 0.0, 0.0));
        helper.assertTrue(position.equals(target.position()), "Native movement must obey overload");
        var health = target.getHealth();
        target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2f);
        helper.assertTrue(target.getHealth() < health, "Overload must not grant environmental invulnerability");
        Domains.end(domain);
        helper.assertTrue(!Domains.isOverloaded(target), "Collapse must release control immediately");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void simultaneousDomainsSuppressBeforeSureHitsAndSupportThreeCasters(GameTestHelper helper) {
        var first = caster(helper);
        var second = caster(helper);
        var third = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        var initial = Domains.open(first, Technique.UNLIMITED_VOID);
        var shrine = Domains.open(second, Technique.MALEVOLENT_SHRINE);
        var extra = Domains.open(third, Technique.UNLIMITED_VOID);
        var health = target.getHealth();
        Domains.tick();
        helper.assertTrue(
                !Domains.isOverloaded(target) && target.getHealth() == health, "Resolve all overlaps before hits");
        helper.assertTrue(
                Domains.inLevel(helper.getLevel()).containsAll(List.of(initial, shrine, extra)),
                "A third caster has no artificial domain cap");
        Domains.end(shrine);
        Domains.end(extra);
        Domains.tick();
        helper.assertTrue(Domains.isOverloaded(target), "The remaining uncontested domain must resume");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void shrineSureHitDoesNotWaitForTerrainCapacityOrVisibility(GameTestHelper helper) {
        var caster = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        helper.setBlock(WALL, Blocks.BEDROCK);
        var reservations = saturateTerrain(helper, caster);
        try {
            Domains.open(caster, Technique.MALEVOLENT_SHRINE);
            Domains.tick();
            helper.assertTrue(
                    target.getHealth() < target.getMaxHealth(),
                    "Sure hits are independent of cover and terrain capacity");
            helper.assertBlockPresent(Blocks.BEDROCK, WALL);
        } finally {
            reservations.forEach(TerrainDestruction.Work::close);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void simpleDomainProtectsNearbyTargetsAndErodesOncePerTick(GameTestHelper helper) {
        var caster = caster(helper);
        var defender = caster(helper);
        defender.setPos(defender.position().add(0.0, 0.0, 4.0));
        CombatRuntime.practice(defender, true);
        var fighter = CombatRuntime.fighter(defender);
        fighter.prepare(Technique.SIMPLE_DOMAIN);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(3, 1, 6));
        Domains.open(caster, Technique.UNLIMITED_VOID);
        try {
            Domains.tick();
            Domains.tick();
            helper.assertTrue(!Domains.isOverloaded(target), "Simple Domain must protect its spatial occupants");
            helper.assertTrue(
                    fighter.defense().simple() == 99,
                    "Extra targets and duplicate same-tick checks must not multiply erosion");
            fighter.prepare(Technique.SIMPLE_DOMAIN);
            Domains.tick();
            helper.assertTrue(Domains.isOverloaded(target), "Losing protection must expose the target");
        } finally {
            CombatRuntime.practice(defender, false);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void burnoutSurvivesReconstructionButDoesNotBlockHealing(GameTestHelper helper) {
        var caster = caster(helper);
        CombatRuntime.practice(caster, true);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        Domains.end(domain);
        var fighter = fighter(helper, caster);
        helper.assertTrue(
                fighter.burnout() == DomainEntity.BURNOUT, "Burnout must be persisted before a domain can disappear");
        fighter.prepare(Technique.BLUE);
        helper.assertTrue(fighter.cast() == null, "Burnout disables the innate technique");
        caster.setHealth(10);
        fighter.prepare(Technique.HEAL);
        var cast = fighter.cast();
        helper.assertTrue(cast != null && cast.technique() == Technique.HEAL, "Learned self-healing remains available");
        fighter.cancel();
        helper.assertTrue(
                caster.getAttachedOrCreate(SorcererAttachments.RESOURCES).burnout() > 0, "Cancel cannot erase burnout");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void nativeRemovalReleasesControlAndKeepsBurnout(GameTestHelper helper) {
        var caster = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        helper.assertTrue(
                Domains.inLevel(helper.getLevel()).contains(domain),
                "Native load indexes the anchor before its first tick");
        Domains.tick();
        helper.assertTrue(Domains.isOverloaded(target), "Target begins controlled");
        domain.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(!Domains.isOverloaded(target), "Native unload clears control immediately");
        helper.assertTrue(!Domains.inLevel(helper.getLevel()).contains(domain), "No stale level index");
        helper.assertTrue(
                caster.getAttachedOrCreate(SorcererAttachments.RESOURCES).burnout() > 0, "Recovery remains saved");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void amplificationBypassesContactButNotAttributedProjectiles(GameTestHelper helper) {
        var defender = caster(helper);
        var attacker = caster(helper);
        CombatRuntime.practice(defender, true);
        CombatRuntime.practice(attacker, true);
        try {
            CombatRuntime.fighter(defender).prepare(Technique.INFINITY);
            var source = helper.getLevel().damageSources().playerAttack(attacker);
            helper.assertTrue(InfinityDefense.blocks(defender, source), "Ordinary melee is blocked");
            CombatRuntime.fighter(attacker).prepare(Technique.AMPLIFICATION);
            helper.assertTrue(!InfinityDefense.blocks(defender, source), "Amplified contact bypasses Infinity");
            var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(2, 2, 2));
            helper.assertTrue(
                    InfinityDefense.blocks(
                            defender, helper.getLevel().damageSources().arrow(arrow, attacker)),
                    "Attributing an arrow to an amplified player cannot bypass contact rules");
            CombatRuntime.fighter(attacker).prepare(Technique.BLUE);
            helper.assertTrue(CombatRuntime.fighter(attacker).cast() == null, "Amplification excludes innate casting");
            arrow.discard();
        } finally {
            CombatRuntime.practice(defender, false);
            CombatRuntime.practice(attacker, false);
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void shrineAdaptsCleaveToDurableTargetsWithoutExceedingItsOutput(GameTestHelper helper) {
        var caster = caster(helper);
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 6));
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(200);
        target.setHealth(200);
        Domains.open(caster, Technique.MALEVOLENT_SHRINE);
        Domains.tick();
        helper.assertTrue(target.getHealth() == 40, "Cleave adapts to durability up to its 160-point limit");
        helper.succeed();
    }
}
