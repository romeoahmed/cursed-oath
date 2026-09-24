package io.github.romeoahmed.cursedoath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.MeleeDamage;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void cursedOath$bindPulse(Entity target, CallbackInfo ci, @Share("synchronizedHit") LocalBooleanRef ready) {
        if ((Object) this instanceof ServerPlayer player) {
            boolean pulse = CombatRuntime.INSTANCE.consumePulse(player);
            ready.set(pulse && player.getAttackStrengthScale(0.5f) > 0.9f);
        }
    }

    @WrapOperation(
            method = "attack",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean cursedOath$primaryHit(
            Entity target,
            DamageSource source,
            float damage,
            Operation<Boolean> original,
            @Share("synchronizedHit") LocalBooleanRef ready) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(target, source, damage);
        if (!ready.get()
                || !(target instanceof LivingEntity living)
                || !TechniqueCombat.INSTANCE.canAffect(player, living)
                || (target instanceof ServerPlayer defender && CombatRuntime.INSTANCE.hasInfinity(defender))) {
            return original.call(target, source, damage);
        }
        boolean blackFlash = player.getRandom().nextFloat() < 0.2f;
        float before = living.getHealth() + living.getAbsorptionAmount();
        boolean hit = original.call(target, source, MeleeDamage.INSTANCE.resolve(damage, blackFlash));
        if (hit && blackFlash && living.getHealth() + living.getAbsorptionAmount() < before) {
            TechniqueCombat.INSTANCE.blackFlash(player, living);
        }
        return hit;
    }
}
