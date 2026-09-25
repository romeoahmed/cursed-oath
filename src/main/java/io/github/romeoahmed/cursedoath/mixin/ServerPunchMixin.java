package io.github.romeoahmed.cursedoath.mixin;

import com.google.errorprone.annotations.Keep;
import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPunchMixin {
    // Evaluate shell contact before vanilla consumes the attack's accumulated strength.
    @Keep
    @Inject(
            method = "handlePunch",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerPlayer;resetAttackStrengthTicker()V"))
    private void cursedOath$consumeMiss(ServerboundPunchPacket unusedPacket, CallbackInfo unusedCallback) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        DomainInteractions.punch(player);
        CombatRuntime.consumePulse(player);
    }
}
