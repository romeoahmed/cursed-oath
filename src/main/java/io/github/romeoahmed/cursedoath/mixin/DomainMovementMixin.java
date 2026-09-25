package io.github.romeoahmed.cursedoath.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class DomainMovementMixin {
    @ModifyReturnValue(method = "collide", at = @At("RETURN"))
    private Vec3 cursedOath$domainBoundary(Vec3 movement) {
        return DomainInteractions.INSTANCE.movement((Entity) (Object) this, movement);
    }
}
