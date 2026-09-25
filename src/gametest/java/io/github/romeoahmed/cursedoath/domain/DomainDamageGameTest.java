package io.github.romeoahmed.cursedoath.domain;

import static io.github.romeoahmed.cursedoath.CombatFixtures.*;
import static io.github.romeoahmed.cursedoath.TestLifecycle.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DomainDamageGameTest {
    private static final long BEFORE_PULSE = 5, AFTER_PULSE = 11, AFTER_SECOND_PULSE = 21;
    private static final int EFFECT_DURATION = 100;
    private static final float FIRST_HEALTH = 108, SECOND_HEALTH = 68;

    @GameTest(environment = "cursed-oath-test:domains")
    public void overloadStopsNativeAttackUseAndCastingButExemptsSpectators(GameTestHelper helper) {
        var caster = caster(helper);
        var victim = caster(helper);
        victim.setPos(victim.position().add(0.0, 0.0, 4.0));
        helper.getLevel().addNewPlayer(victim);
        CombatRuntime.practice(victim, true);
        var fighter = CombatRuntime.fighter(victim);
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 7));
        victim.setItemInHand(InteractionHand.MAIN_HAND, Items.APPLE.getDefaultInstance());
        victim.startUsingItem(InteractionHand.MAIN_HAND);
        Domains.open(caster, Technique.UNLIMITED_VOID);
        Domains.tick();
        helper.assertTrue(Domains.isOverloaded(victim) && !victim.isUsingItem(), "Overload interrupts native item use");
        var before = target.getHealth();
        victim.attack(target);
        helper.assertTrue(target.getHealth() == before, "An overloaded player's native attack cannot damage others");
        fighter.prepare(Technique.BLUE);
        helper.assertTrue(fighter.cast() == null, "Overload prevents casting");
        victim.setGameMode(GameType.SPECTATOR);
        Domains.tick();
        helper.assertTrue(!Domains.isOverloaded(victim), "A spectator camera must not be controlled by sure hits");
        helper.succeed();
    }

    @GameTest(environment = "cursed-oath-test:domain_damage", maxTicks = 100)
    @SuppressWarnings("ReferenceEquality") // Verify live entity ownership, not equality by entity ID.
    public void overloadIsLethalWhileCountermeasuresAndNativeDeathProtectionRemainEffective(GameTestHelper helper) {
        var caster = caster(helper);
        var armored = armoredTarget(helper);
        var durable = durableTarget(helper);
        var rescued = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(3, 1, 6));
        rescued.setItemSlot(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.getDefaultInstance());
        var touching = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
        touching.setPos(caster.position());
        var defender = caster(helper);
        defender.setPos(defender.position().add(0.0, 0.0, 11.0));
        CombatRuntime.practice(defender, true);
        var defense = CombatRuntime.fighter(defender);
        defense.prepare(Technique.SIMPLE_DOMAIN);
        var protectedTarget = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 13));
        var domain = Domains.open(caster, Technique.UNLIMITED_VOID);
        helper.runAfterDelay(BEFORE_PULSE, () -> {
            helper.assertTrue(
                    armored.isAlive() && Domains.isOverloaded(armored), "Control starts before the first damage pulse");
            durable.hurtServer(
                    helper.getLevel(), helper.getLevel().damageSources().generic(), 60f);
            durable.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, EFFECT_DURATION, 1));
        });
        helper.runAfterDelay(AFTER_PULSE, () -> {
            helper.assertTrue(
                    !armored.isAlive(), "Armor, Protection, Resistance and cover cannot prevent overload death");
            helper.assertTrue(
                    requireNonNull(armored.getLastDamageSource()).getEntity() == caster,
                    "The caster receives damage attribution");
            helper.assertTrue(
                    durable.getHealth() == FIRST_HEALTH,
                    "Overload bypasses a recent hit cooldown but respects absorption");
            helper.assertTrue(
                    rescued.isAlive() && rescued.getOffhandItem().isEmpty(),
                    "Native death protection consumes the totem");
            helper.assertTrue(
                    protectedTarget.getHealth() == protectedTarget.getMaxHealth(),
                    "Simple Domain prevents information damage");
            helper.assertTrue(
                    touching.getHealth() == touching.getMaxHealth(), "Contact exemption prevents information damage");
        });
        helper.runAfterDelay(AFTER_SECOND_PULSE, () -> {
            helper.assertTrue(durable.getHealth() == SECOND_HEALTH, "Sustained exposure delivers another full pulse");
            helper.assertTrue(!rescued.isAlive(), "A totem is not permanent immunity to continued exposure");
            var opponent = Domains.open(caster(helper), Technique.UNLIMITED_VOID);
            helper.runAfterDelay(AFTER_PULSE, () -> {
                helper.assertTrue(
                        durable.getHealth() == SECOND_HEALTH, "Clashing domains suppress damage as well as control");
                Domains.end(opponent);
                Domains.end(domain);
                defense.cancel();
                CombatRuntime.practice(defender, false);
                helper.runAfterDelay(AFTER_PULSE, () -> {
                    helper.assertTrue(
                            durable.getHealth() == SECOND_HEALTH,
                            "Collapse stops further damage without restoring lost health");
                    helper.assertTrue(!Domains.isOverloaded(durable), "Collapse clears control");
                    helper.succeed();
                });
            });
        });
    }

    private static Villager durableTarget(GameTestHelper helper) {
        var target = stationaryTarget(helper, EntityTypes.VILLAGER, new BlockPos(4, 1, 6));
        requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(200);
        target.setHealth(200);
        return target;
    }

    private static Husk armoredTarget(GameTestHelper helper) {
        var target = stationaryTarget(helper, EntityTypes.HUSK, new BlockPos(2, 1, 6));
        var chest = Items.NETHERITE_CHESTPLATE.getDefaultInstance();
        chest.enchant(
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.PROTECTION),
                4);
        target.setItemSlot(EquipmentSlot.CHEST, chest);
        target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EFFECT_DURATION, 4));
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.BEDROCK);
        return target;
    }
}
