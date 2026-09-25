package io.github.romeoahmed.cursedoath.mixin;

import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.technique.InfinityDefense;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractArrow.class, ThrowableProjectile.class, AbstractHurtingProjectile.class})
abstract class ProjectileDefenseMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cursedOath$intercept(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        InfinityDefense.INSTANCE.interceptArrow(projectile);
        if (DomainInteractions.INSTANCE.projectile(projectile)) ci.cancel();
    }
}
