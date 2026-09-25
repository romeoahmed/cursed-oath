package io.github.romeoahmed.cursedoath.domain;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.technique.CleaveContact;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.world.SweptVolume;
import java.util.HashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DomainBarrierGameTest {
    private static final BlockPos CHEST = new BlockPos(3, 1, 3);

    @GameTest(environment = "cursed-oath-test:domains")
    public void nativeProjectileKeepsShellCollisionsAfterItsOwnerChangesLevel(GameTestHelper helper) {
        var caster = caster(helper);
        var attacker = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(2, 2, 2));
        arrow.setOwner(attacker);
        arrow.setPos(domain.position().add(0, domain.radius() + 1, 0));
        arrow.setDeltaMovement(new Vec3(0, -8, 0));
        var otherLevel = requireNonNull(helper.getLevel().getServer().getLevel(Level.NETHER));
        attacker.setServerLevel(otherLevel);
        try {
            arrow.tick();
            helper.assertTrue(arrow.isRemoved(), "Shell collisions must use the projectile's level");
            helper.assertTrue(domain.shell() < DomainEntity.SHELL_STRENGTH, "Damage must reach the original shell");
        } finally {
            attacker.setServerLevel(helper.getLevel());
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void nativePunchUsesStrengthBeforeThePacketResetsItsCooldown(GameTestHelper helper) {
        var caster = caster(helper);
        var attacker = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        for (int tick = 0; tick < 20; tick++) attacker.doTick();
        attacker.setPos(domain.position().add(0.0, domain.radius() + 1, 0.0));
        attacker.setXRot(90);
        requireNonNull(attacker.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(10.0);
        helper.assertTrue(attacker.getAttackStrengthScale(0.5f) == 1f, "Begin with a fully charged attack");
        attacker.connection.handlePunch(ServerboundPunchPacket.INSTANCE);
        helper.assertTrue(domain.shell() == 90f, "A charged native punch must deal its full ten points");
        attacker.connection.handlePunch(ServerboundPunchPacket.INSTANCE);
        helper.assertTrue(domain.shell() == 89f, "Immediate repeated punches remain limited by vanilla cooldown");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void externalAttacksBreakTheShellWithoutReplacingBuildings(GameTestHelper helper) {
        var caster = caster(helper);
        var rescuer = caster(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var start = domain.position().add(0.0, 0.0, domain.radius() + 2);
        helper.assertTrue(
                DomainInteractions.strike(rescuer, start, domain.position(), DomainEntity.SHELL_STRENGTH),
                "Hit the actual shell");
        helper.assertTrue(domain.isRemoved(), "Sufficient outside damage must collapse the shell");
        helper.assertBlockPresent(Blocks.CHEST, CHEST);
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void nativeProjectileHitsShellBeforeReachingItsInterior(GameTestHelper helper) {
        var caster = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(2, 2, 2));
        arrow.setPos(domain.position().add(0.0, domain.radius() + 1, 0.0));
        arrow.setDeltaMovement(new Vec3(0.0, -8.0, 0.0));
        arrow.tick();
        helper.assertTrue(arrow.isRemoved(), "Arrow must hit the shell before its own collision tick");
        helper.assertTrue(domain.shell() < DomainEntity.SHELL_STRENGTH, "Projectile damage must reach the shell");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void terrainBeforeTheShellAndCasterOwnershipPreventProjectileShellDamage(GameTestHelper helper) {
        var caster = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(2, 2, 2));
        var wall = BlockPos.containing(domain.position().add(0.0, domain.radius() + 2, 0.0));
        try {
            arrow.setPos(domain.position().add(0.0, domain.radius() + 4, 0.0));
            arrow.setDeltaMovement(new Vec3(0.0, -8.0, 0.0));
            helper.getLevel().setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!DomainInteractions.projectile(arrow), "Native terrain gets the nearer hit");
            helper.assertTrue(domain.shell() == DomainEntity.SHELL_STRENGTH, "A wall shields the shell");
            helper.getLevel().removeBlock(wall, false);
            arrow.setOwner(caster);
            helper.assertTrue(!DomainInteractions.projectile(arrow), "The caster's projectile passes their own shell");
            helper.assertTrue(!arrow.isRemoved(), "An exempt projectile remains available for native movement");
            helper.assertTrue(domain.shell() == DomainEntity.SHELL_STRENGTH, "Exemption cannot damage the shell");
        } finally {
            helper.getLevel().removeBlock(wall, false);
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void grazingVolumeBreaksShellWithoutACentreLineHit(GameTestHelper helper) {
        var caster = caster(helper);
        var attacker = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var start = domain.position().add(domain.radius() + 1, 0.0, -2.0);
        var end = start.add(0.0, 0.0, 4.0);
        helper.assertTrue(DomainInteractions.contact(attacker, start, end) == null, "Centre ray misses");
        DomainInteractions.pierce(
                helper.getLevel(),
                attacker,
                new SweptVolume(start, end, new Vec3(2.0, 2.0, 2.0), true),
                2.0,
                DomainEntity.SHELL_STRENGTH,
                new HashSet<>());
        helper.assertTrue(domain.isRemoved(), "The swept volume must still graze and break the shell");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domains")
    public void cleaveStrikesTheShellBeforeTheEntityBehindIt(GameTestHelper helper) {
        var caster = caster(helper);
        var attacker = caster(helper);
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        target.setPos(domain.position().add(0.0, domain.radius() - 3, 0.0));
        attacker.setPos(domain.position().add(0.0, domain.radius(), 0.0));
        attacker.setXRot(90);
        try (var work = requireNonNull(reserveTerrain(helper, attacker))) {
            var health = target.getHealth();
            var impact = CleaveContact.release(attacker, work);
            helper.assertTrue(impact != null, "An empty shell surface is a valid contact");
            helper.assertTrue(domain.shell() < DomainEntity.SHELL_STRENGTH, "Cleave damages the shell");
            helper.assertTrue(target.getHealth() == health, "The shell absorbs this contact before the entity");
            helper.assertTrue(work.finished(), "Shell contact must not schedule an unbounded terrain lattice");
        }
        helper.succeed();
    }
}
