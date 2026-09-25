package io.github.romeoahmed.cursedoath.domain

import io.github.romeoahmed.cursedoath.caster
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.stationaryTarget
import io.github.romeoahmed.cursedoath.technique.Technique
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks

class DomainDamageGameTest {
    @GameTest(environment = "cursed-oath-test:domains")
    fun overloadStopsNativeAttackUseAndCastingButExemptsSpectators(helper: GameTestHelper) {
        val caster = helper.caster()
        val separation = 4.0
        val victim = helper.caster().apply { setPos(position().add(0.0, 0.0, separation)) }
        helper.level.addNewPlayer(victim)
        CombatRuntime.practice(victim, true)
        val fighter = CombatRuntime.fighter(victim)
        val target = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(2, 1, 7))
        victim.setItemInHand(InteractionHand.MAIN_HAND, Items.APPLE.defaultInstance)
        victim.startUsingItem(InteractionHand.MAIN_HAND)
        Domains.open(caster, Technique.UNLIMITED_VOID)
        Domains.tick()
        helper.assertTrue(Domains.isOverloaded(victim) && !victim.isUsingItem, "Overload interrupts native item use")
        val before = target.health
        victim.attack(target)
        helper.assertTrue(target.health == before, "An overloaded player's native attack cannot damage others")
        fighter.prepare(Technique.BLUE)
        helper.assertTrue(fighter.cast == null, "Overload prevents casting")
        victim.setGameMode(GameType.SPECTATOR)
        Domains.tick()
        helper.assertTrue(!Domains.isOverloaded(victim), "A spectator camera must not be controlled by sure hits")
        helper.succeed()
    }

    @GameTest(environment = "cursed-oath-test:domain_damage", maxTicks = 100)
    fun overloadIsLethalWhileCountermeasuresAndNativeDeathProtectionRemainEffective(helper: GameTestHelper) {
        val caster = helper.caster()
        val armored = armoredTarget(helper)
        val durable = durableTarget(helper)
        val rescued = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(3, 1, 6))
        rescued.setItemSlot(EquipmentSlot.OFFHAND, Items.TOTEM_OF_UNDYING.defaultInstance)
        val touching = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(2, 1, 1))
        touching.setPos(caster.position())
        val defender = helper.caster().apply { setPos(position().add(0.0, 0.0, PROTECTOR_OFFSET)) }
        CombatRuntime.practice(defender, true)
        val defense = CombatRuntime.fighter(defender)
        defense.prepare(Technique.SIMPLE_DOMAIN)
        val protected = helper.stationaryTarget(EntityTypes.VILLAGER, BlockPos(2, 1, 13))
        val domain = Domains.open(caster, Technique.UNLIMITED_VOID)
        helper.runAfterDelay(BEFORE_PULSE) {
            helper.assertTrue(
                armored.isAlive && Domains.isOverloaded(armored),
                "Control starts before the first damage pulse",
            )
            durable.hurtServer(helper.level, helper.level.damageSources().generic(), PRIOR_DAMAGE)
            durable.addEffect(MobEffectInstance(MobEffects.ABSORPTION, EFFECT_DURATION, 1))
        }
        helper.runAfterDelay(AFTER_PULSE) {
            helper.assertTrue(!armored.isAlive, "Armor, Protection, Resistance and cover cannot prevent overload death")
            helper.assertTrue(armored.lastDamageSource?.entity === caster, "The caster receives damage attribution")
            helper.assertTrue(
                durable.health == FIRST_HEALTH,
                "Overload bypasses a recent hit cooldown but respects absorption",
            )
            helper.assertTrue(
                rescued.isAlive && rescued.offhandItem.isEmpty,
                "Native death protection consumes the totem",
            )
            helper.assertTrue(protected.health == protected.maxHealth, "Simple Domain prevents information damage")
            helper.assertTrue(touching.health == touching.maxHealth, "Contact exemption prevents information damage")
        }
        helper.runAfterDelay(AFTER_SECOND_PULSE) {
            helper.assertTrue(durable.health == SECOND_HEALTH, "Sustained exposure delivers another full pulse")
            helper.assertTrue(!rescued.isAlive, "A totem is not permanent immunity to continued exposure")
            val opponent = Domains.open(helper.caster(), Technique.UNLIMITED_VOID)
            helper.runAfterDelay(AFTER_PULSE) {
                helper.assertTrue(
                    durable.health == SECOND_HEALTH,
                    "Clashing domains suppress damage as well as control",
                )
                Domains.end(opponent)
                Domains.end(domain)
                defense.cancel()
                CombatRuntime.practice(defender, false)
                helper.runAfterDelay(AFTER_PULSE) {
                    helper.assertTrue(
                        durable.health == SECOND_HEALTH,
                        "Collapse stops further damage without restoring lost health",
                    )
                    helper.assertTrue(!Domains.isOverloaded(durable), "Collapse clears control")
                    helper.succeed()
                }
            }
        }
    }

    private fun durableTarget(helper: GameTestHelper) =
        helper.stationaryTarget(EntityTypes.VILLAGER, DURABLE_POSITION).apply {
            checkNotNull(getAttribute(Attributes.MAX_HEALTH)).baseValue = HEALTH.toDouble()
            health = HEALTH
        }

    private fun armoredTarget(helper: GameTestHelper) =
        helper.stationaryTarget(EntityTypes.HUSK, ARMORED_POSITION).apply {
            val chest = Items.NETHERITE_CHESTPLATE.defaultInstance
            chest.enchant(
                helper.level
                    .registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION),
                PROTECTION_LEVEL,
            )
            setItemSlot(EquipmentSlot.CHEST, chest)
            addEffect(MobEffectInstance(MobEffects.RESISTANCE, EFFECT_DURATION, RESISTANCE_LEVEL))
            helper.setBlock(WALL, Blocks.BEDROCK)
        }

    private companion object {
        const val PROTECTION_LEVEL = 4
        const val RESISTANCE_LEVEL = 4
        const val BEFORE_PULSE = 5L
        const val AFTER_PULSE = 11L
        const val AFTER_SECOND_PULSE = 21L
        val ARMORED_POSITION = BlockPos(2, 1, 6)
        val DURABLE_POSITION = BlockPos(4, 1, 6)
        val WALL = BlockPos(2, 2, 4)
        const val HEALTH = 200f
        const val PRIOR_DAMAGE = 60f
        const val FIRST_HEALTH = 108f
        const val SECOND_HEALTH = 68f
        const val EFFECT_DURATION = 100
        const val PROTECTOR_OFFSET = 11.0
    }
}
