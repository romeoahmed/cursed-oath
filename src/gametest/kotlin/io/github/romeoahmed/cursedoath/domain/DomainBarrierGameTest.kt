package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.reserveTerrain
import io.github.romeoahmed.cursedoath.stationaryTarget
import io.github.romeoahmed.cursedoath.technique.BlueField
import io.github.romeoahmed.cursedoath.technique.CleaveContact
import io.github.romeoahmed.cursedoath.technique.RedBlast
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.world.SweptVolume
import io.github.romeoahmed.cursedoath.world.TerrainDestruction
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class DomainBarrierGameTest {
    @GameTest(environment = "cursed-oath-test:domains")
    fun nativePunchUsesStrengthBeforeThePacketResetsItsCooldown(helper: GameTestHelper) {
        val caster = helper.caster()
        val attacker = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        repeat(CHARGE_TICKS) { attacker.doTick() }
        attacker.setPos(domain.position().add(0.0, domain.radius + 1, 0.0))
        attacker.xRot = DOWNWARD_PITCH
        checkNotNull(attacker.getAttribute(Attributes.ATTACK_DAMAGE)).baseValue = PUNCH_DAMAGE
        helper.assertTrue(
            attacker.getAttackStrengthScale(ATTACK_PARTIAL_TICK) == 1f,
            "Begin with a fully charged attack",
        )
        attacker.connection.handlePunch(ServerboundPunchPacket.INSTANCE)
        helper.assertTrue(
            domain.shell == AFTER_CHARGED_PUNCH,
            "A charged native punch must deal its full ten points",
        )
        attacker.connection.handlePunch(ServerboundPunchPacket.INSTANCE)
        helper.assertTrue(
            domain.shell == AFTER_REPEATED_PUNCH,
            "Immediate repeated punches remain limited by vanilla cooldown",
        )
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun externalAttacksBreakTheShellWithoutReplacingBuildings(helper: GameTestHelper) {
        val caster = helper.caster()
        val rescuer = helper.caster()
        helper.setBlock(CHEST, Blocks.CHEST)
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val start = domain.position().add(0.0, 0.0, domain.radius + 2)
        val end = domain.position()
        helper.assertTrue(
            DomainInteractions.strike(rescuer, start, end, DomainEntity.SHELL_STRENGTH),
            "Hit the actual shell",
        )
        helper.assertTrue(domain.isRemoved, "Sufficient outside damage must collapse the shell")
        helper.assertBlockPresent(Blocks.CHEST, CHEST)
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun nativeProjectileHitsShellBeforeReachingItsInterior(helper: GameTestHelper) {
        val caster = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val arrow = helper.spawn(EntityTypes.ARROW, BlockPos(2, 2, 2))
        arrow.setPos(domain.position().add(0.0, domain.radius + 1, 0.0))
        arrow.deltaMovement = Vec3(0.0, ARROW_SPEED, 0.0)
        arrow.tick()
        helper.assertTrue(arrow.isRemoved, "Arrow must hit the shell before its own collision tick")
        helper.assertTrue(domain.shell < DomainEntity.SHELL_STRENGTH, "Projectile damage must reach the shell")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun terrainBeforeTheShellAndCasterOwnershipPreventProjectileShellDamage(helper: GameTestHelper) {
        val caster = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val arrow = helper.spawn(EntityTypes.ARROW, BlockPos(2, 2, 2))
        val wall = BlockPos.containing(domain.position().add(0.0, domain.radius + 2, 0.0))
        try {
            arrow.setPos(domain.position().add(0.0, domain.radius + ARROW_HEIGHT, 0.0))
            arrow.deltaMovement = Vec3(0.0, ARROW_SPEED, 0.0)
            helper.level.setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState())
            helper.assertTrue(!DomainInteractions.projectile(arrow), "Native terrain gets the nearer hit")
            helper.assertTrue(domain.shell == DomainEntity.SHELL_STRENGTH, "A wall shields the shell")
            helper.level.removeBlock(wall, false)
            arrow.setOwner(caster)
            helper.assertTrue(!DomainInteractions.projectile(arrow), "The caster's projectile passes their own shell")
            helper.assertTrue(!arrow.isRemoved, "An exempt projectile remains available for native movement")
            helper.assertTrue(domain.shell == DomainEntity.SHELL_STRENGTH, "Exemption cannot damage the shell")
        } finally {
            helper.level.removeBlock(wall, false)
            arrow.discard()
        }
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun grazingVolumeBreaksShellWithoutACentreLineHit(helper: GameTestHelper) {
        val caster = helper.caster()
        val attacker = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val start = domain.position().add(domain.radius + 1, 0.0, -2.0)
        val end = start.add(0.0, 0.0, 4.0)
        helper.assertTrue(DomainInteractions.contact(attacker, start, end) == null, "Centre ray misses")
        val volume = SweptVolume(start, end, Vec3(2.0, 2.0, 2.0), true)
        DomainInteractions.pierce(attacker, volume, 2.0, DomainEntity.SHELL_STRENGTH, hashSetOf())
        helper.assertTrue(domain.isRemoved, "The swept volume must still graze and break the shell")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun orbFieldsCannotReachThroughAnIntactShell(helper: GameTestHelper) {
        val caster = helper.caster()
        val attacker = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        target.setPos(domain.position().add(0.0, domain.radius - 2, 0.0))
        val impact = domain.position().add(0.0, domain.radius + 1, 0.0)
        val health = target.health
        BlueField.tick(attacker, impact, 1)
        RedBlast.impact(attacker, impact, Vec3(0.0, -1.0, 0.0))
        helper.assertTrue(target.health == health, "The shell must stop secondary damage")
        helper.assertTrue(target.deltaMovement == Vec3.ZERO, "The shell must stop secondary forces")
        Domains.end(domain)
        BlueField.tick(attacker, impact, 1)
        helper.assertTrue(target.deltaMovement.y > 0, "The same field reaches its target after collapse")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domains")
    fun cleaveStrikesTheShellBeforeTheEntityBehindIt(helper: GameTestHelper) {
        val caster = helper.caster()
        val attacker = helper.caster()
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        val target = helper.stationaryTarget(EntityTypes.HUSK, BlockPos(2, 1, 6))
        target.setPos(domain.position().add(0.0, domain.radius - TARGET_DEPTH, 0.0))
        attacker.setPos(domain.position().add(0.0, domain.radius, 0.0))
        attacker.xRot = DOWNWARD_PITCH
        val work = checkNotNull(helper.reserveTerrain(attacker))
        try {
            val health = target.health
            val impact = CleaveContact.release(attacker, work)
            helper.assertTrue(impact != null, "An empty shell surface is a valid contact")
            helper.assertTrue(domain.shell < DomainEntity.SHELL_STRENGTH, "Cleave damages the shell")
            helper.assertTrue(target.health == health, "The shell absorbs this contact before the entity")
            helper.assertTrue(work.finished, "Shell contact must not schedule an unbounded terrain lattice")
        } finally {
            work.close()
        }
        helper.succeed()
    }

    private companion object {
        const val CHARGE_TICKS = 20
        const val PUNCH_DAMAGE = 10.0
        const val ATTACK_PARTIAL_TICK = 0.5f
        const val AFTER_CHARGED_PUNCH = 90f
        const val AFTER_REPEATED_PUNCH = 89f
        const val ARROW_HEIGHT = 4
        const val ARROW_SPEED = -8.0
        const val DOWNWARD_PITCH = 90f
        const val TARGET_DEPTH = 3
        val CHEST = BlockPos(3, 1, 3)
    }
}
