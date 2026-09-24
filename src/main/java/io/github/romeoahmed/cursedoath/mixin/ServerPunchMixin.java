package io.github.romeoahmed.cursedoath.mixin;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPunchMixin {
    @Inject(method = "handlePunch", at = @At("TAIL"))
    private void cursedOath$consumeMiss(ServerboundPunchPacket packet, CallbackInfo ci) {
        CombatRuntime.INSTANCE.consumePulse(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
