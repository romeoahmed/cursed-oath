package io.github.romeoahmed.cursedoath.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.romeoahmed.cursedoath.domain.Domains;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class DomainImmobilityMixin {
    @ModifyReturnValue(method = "isImmobile", at = @At("RETURN"))
    private boolean cursedOath$overload(boolean original) {
        return original || Domains.INSTANCE.isOverloaded((LivingEntity) (Object) this);
    }
}
