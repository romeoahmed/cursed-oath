package io.github.romeoahmed.cursedoath.mixin;

import com.google.errorprone.annotations.Keep;
import io.github.romeoahmed.cursedoath.domain.DomainInteractions;
import io.github.romeoahmed.cursedoath.domain.Domains;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class DomainPlayerMovementMixin {
    @Keep
    @Inject(
            method = "handleMovePlayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void cursedOath$validateDomainMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        Vec3 requested = new Vec3(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()))
                .subtract(player.position());
        if (!Double.isFinite(requested.lengthSqr())) return;
        Vec3 permitted = DomainInteractions.movement(player, requested);
        if (permitted.distanceToSqr(requested) > 1.0e-10) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            ci.cancel();
        }
    }

    @Keep
    @Inject(
            method = "handleMoveVehicle",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    @SuppressWarnings("ReferenceEquality") // Check the actual controlled instance.
    private void cursedOath$validateVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        Entity vehicle = player.getRootVehicle();
        if (vehicle == player || vehicle.getControllingPassenger() != player) return;
        Vec3 requested = packet.movingTo().position().subtract(vehicle.position());
        if (!Double.isFinite(requested.lengthSqr())) return;
        if (Domains.isOverloaded(player)
                || DomainInteractions.movement(vehicle, requested).distanceToSqr(requested) > 1.0e-10) {
            player.connection.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
            ci.cancel();
        }
    }
}
