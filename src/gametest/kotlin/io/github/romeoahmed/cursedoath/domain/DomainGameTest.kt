package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.allowDamage
import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.combat.Defense
import io.github.romeoahmed.cursedoath.combat.SorcererData
import io.github.romeoahmed.cursedoath.fighter
import io.github.romeoahmed.cursedoath.reserveTerrain
import io.github.romeoahmed.cursedoath.stationaryTarget
import io.github.romeoahmed.cursedoath.technique.BlueField
import io.github.romeoahmed.cursedoath.technique.CleaveContact
import io.github.romeoahmed.cursedoath.technique.InfinityDefense
import io.github.romeoahmed.cursedoath.technique.RedBlast
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.world.SweptVolume
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class DomainGameTest {
    @GameTest(environment = "cursed-oath-test:domains")
    fun collapseDuringSureHitStopsRemainingTargetsAndTerrain(helper: GameTestHelper) {
        val caster = helper.caster()
        val targets =
            listOf(
                helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6)),
                helper.stationaryTarget(EntityTypes.HUSK, BlockPos(3, 1, 6)),
            )
        val domain = Domains.open(caster, Technique.MALEVOLENT_SHRINE)
        helper.allowDamage { target, source, _ ->
            if (target in targets && source.directEntity === domain) Domains.end(domain)
            true
        }
        Domains.tick()
        helper.assertTrue(domain.isRemoved, "The damage callback must collapse the anchor")
        helper.assertTrue(
            targets.count { it.health < it.maxHealth } == 1,
            "Collapse must stop remaining sure hits: ${targets.map { it.health }}",
        )
        helper.assertTrue(domain.terrain == null, "A removed anchor must not reserve more excavation")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun damageCallbackMovingAnotherTargetOutsideStopsItsSureHit(helper: GameTestHelper) {
        val caster = helper.caster()
        val targets =
            listOf(
                helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6)),
                helper.stationaryTarget(EntityTypes.HUSK, BlockPos(3, 1, 6)),
            )
        val domain = Domains.open(caster, Technique.MALEVOLENT_SHRINE)
        var relocated = false
        helper.allowDamage { target, source, _ ->
            if (!relocated && source.directEntity === domain && target in targets) {
                relocated = true
                for (other in targets) {
                    if (other !== target) other.setPos(domain.position().add(domain.radius * 2, 0.0, 0.0))
                }
            }
            true
        }
        Domains.tick()
        helper.assertTrue(relocated, "The first hit must invoke the relocation callback")
        helper.assertTrue(targets.count { it.health < it.maxHealth } == 1, "Recheck targets after damage callbacks")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun contactExemptionDoesNotErodeSimpleDomain(helper: GameTestHelper) {
        val caster = helper.caster()
        val defender = helper.caster()
        CombatRuntime.practice(defender, true)
        val fighter = CombatRuntime.fighter(defender)
        fighter.prepare(Technique.SIMPLE_DOMAIN)
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 1))
        target.setPos(caster.position())
        Domains.open(caster, Technique.UNLIMITED_VOID)
        try {
            Domains.tick()
            helper.assertTrue(!Domains.isOverloaded(target), "Contact exempts the target")
            helper.assertTrue(fighter.defense.simple == Defense.SIMPLE_STRENGTH, "An exempt target exerts no pressure")
            target.setPos(caster.position().add(0.0, 0.0, 2.0))
            Domains.tick()
            helper.assertTrue(
                fighter.defense.simple == Defense.SIMPLE_STRENGTH - 1,
                "Breaking contact resumes pressure",
            )
        } finally {
            CombatRuntime.practice(defender, false)
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun voidControlsNativeMovementAndReleasesOnCollapse(helper: GameTestHelper) {
        val caster = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        Domains.tick()
        helper.assertTrue(Domains.isOverloaded(target), "Void must apply its own control state")
        val position = target.position()
        target.move(MoverType.SELF, Vec3(1.0, 0.0, 0.0))
        helper.assertTrue(position == target.position(), "Native movement must obey overload")
        val health = target.health
        target.hurtServer(helper.level, helper.level.damageSources().generic(), 2f)
        helper.assertTrue(target.health < health, "Overload must not grant environmental invulnerability")
        Domains.end(domain)
        helper.assertTrue(!Domains.isOverloaded(target), "Collapse must release control immediately")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun simultaneousDomainsSuppressBeforeSureHitsAndSupportThreeCasters(helper: GameTestHelper) {
        val first = helper.caster()
        val second = helper.caster()
        val third = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        val initial = Domains.open(first, Technique.UNLIMITED_VOID)
        val shrine = Domains.open(second, Technique.MALEVOLENT_SHRINE)
        val extra = Domains.open(third, Technique.UNLIMITED_VOID)
        val health = target.health
        Domains.tick()
        helper.assertTrue(
            !Domains.isOverloaded(target) && target.health == health,
            "Resolve all overlaps before hits",
        )
        helper.assertTrue(
            Domains.inLevel(helper.level).containsAll(listOf(initial, shrine, extra)),
            "A third caster has no artificial domain cap",
        )
        Domains.end(shrine)
        Domains.end(extra)
        Domains.tick()
        helper.assertTrue(Domains.isOverloaded(target), "The remaining uncontested domain must resume")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun shrineSureHitDoesNotWaitForTerrainCapacityOrVisibility(helper: GameTestHelper) {
        val caster = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        helper.setBlock(WALL, Blocks.BEDROCK)
        val reservations = generateSequence { helper.reserveTerrain(caster) }.toList()
        try {
            Domains.open(caster, Technique.MALEVOLENT_SHRINE)
            Domains.tick()
            helper.assertTrue(
                target.health < target.maxHealth,
                "Sure hits are independent of cover and terrain capacity",
            )
            helper.assertBlockPresent(Blocks.BEDROCK, WALL)
        } finally {
            reservations.forEach { it.close() }
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun simpleDomainProtectsNearbyTargetsAndErodesOncePerTick(helper: GameTestHelper) {
        val caster = helper.caster()
        val defender = helper.caster()
        defender.setPos(defender.position().add(0.0, 0.0, PROTECTOR_OFFSET))
        CombatRuntime.practice(defender, true)
        val fighter = CombatRuntime.fighter(defender)
        fighter.prepare(Technique.SIMPLE_DOMAIN)
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        helper.stationaryTarget(EntityTypes.HUSK, SECOND_TARGET)
        Domains.open(caster, Technique.UNLIMITED_VOID)
        try {
            Domains.tick()
            Domains.tick()
            helper.assertTrue(!Domains.isOverloaded(target), "Simple Domain must protect its spatial occupants")
            helper.assertTrue(
                fighter.defense.simple == REMAINING_PROTECTION,
                "Extra targets and duplicate same-tick checks must not multiply erosion",
            )
            fighter.prepare(Technique.SIMPLE_DOMAIN)
            Domains.tick()
            helper.assertTrue(Domains.isOverloaded(target), "Losing protection must expose the target")
        } finally {
            CombatRuntime.practice(defender, false)
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun burnoutSurvivesReconstructionButDoesNotBlockHealing(helper: GameTestHelper) {
        val caster = helper.caster()
        CombatRuntime.practice(caster, true)
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        Domains.end(domain)
        val fighter = helper.fighter(caster)
        helper.assertTrue(
            fighter.burnout == DomainEntity.BURNOUT,
            "Burnout must be persisted before a domain can disappear",
        )
        fighter.prepare(Technique.BLUE)
        helper.assertTrue(fighter.cast == null, "Burnout disables the innate technique")
        caster.health = WOUNDED_HEALTH
        fighter.prepare(Technique.HEAL)
        helper.assertTrue(fighter.cast?.technique == Technique.HEAL, "Learned self-healing remains available")
        fighter.cancel()
        helper.assertTrue(
            caster.getAttachedOrCreate(SorcererData.RESOURCES).burnout > 0,
            "Cancel cannot erase burnout",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun nativeRemovalReleasesControlAndKeepsBurnout(helper: GameTestHelper) {
        val caster = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        helper.assertTrue(
            domain in Domains.inLevel(helper.level),
            "Native load indexes the anchor before its first tick",
        )
        Domains.tick()
        helper.assertTrue(Domains.isOverloaded(target), "Target begins controlled")
        domain.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK)
        helper.assertTrue(!Domains.isOverloaded(target), "Native unload clears control immediately")
        helper.assertTrue(Domains.inLevel(helper.level).none { it === domain }, "No stale level index")
        helper.assertTrue(caster.getAttachedOrCreate(SorcererData.RESOURCES).burnout > 0, "Recovery remains saved")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun amplificationBypassesContactButNotAttributedProjectiles(helper: GameTestHelper) {
        val defender = helper.caster()
        val attacker = helper.caster()
        CombatRuntime.practice(defender, true)
        CombatRuntime.practice(attacker, true)
        try {
            CombatRuntime.fighter(defender).prepare(Technique.INFINITY)
            val source = helper.level.damageSources().playerAttack(attacker)
            helper.assertTrue(InfinityDefense.blocks(defender, source), "Ordinary melee is blocked")
            CombatRuntime.fighter(attacker).prepare(Technique.AMPLIFICATION)
            helper.assertTrue(!InfinityDefense.blocks(defender, source), "Amplified contact bypasses Infinity")
            val arrow = helper.spawn(EntityTypes.ARROW, BlockPos(2, 2, 2))
            helper.assertTrue(
                InfinityDefense.blocks(defender, helper.level.damageSources().arrow(arrow, attacker)),
                "Attributing an arrow to an amplified player cannot bypass contact rules",
            )
            CombatRuntime.fighter(attacker).prepare(Technique.BLUE)
            helper.assertTrue(CombatRuntime.fighter(attacker).cast == null, "Amplification excludes innate casting")
            arrow.discard()
        } finally {
            CombatRuntime.practice(defender, false)
            CombatRuntime.practice(attacker, false)
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun shrineAdaptsCleaveToDurableTargetsWithoutExceedingItsOutput(helper: GameTestHelper) {
        val caster = helper.caster()
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(2, 1, 6))
        target.getAttribute(Attributes.MAX_HEALTH)?.baseValue = DURABLE_HEALTH.toDouble()
        target.health = DURABLE_HEALTH
        Domains.open(caster, Technique.MALEVOLENT_SHRINE)
        Domains.tick()
        helper.assertTrue(target.health == AFTER_CLEAVE, "Cleave adapts to durability up to its 160-point limit")
        helper.succeed()
    }

    private companion object {
        const val DURABLE_HEALTH = 200f
        const val AFTER_CLEAVE = 40f
        const val PROTECTOR_OFFSET = 4.0
        const val REMAINING_PROTECTION = 99
        const val WOUNDED_HEALTH = 10f
        val WALL = BlockPos(2, 2, 4)
        val SECOND_TARGET = BlockPos(3, 1, 6)
    }
}
