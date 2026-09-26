package io.github.romeoahmed.cursedoath.mixin;

import com.google.errorprone.annotations.Keep;
import io.github.romeoahmed.cursedoath.domain.DomainIndex;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Tints native air fog inside Shrine while preserving shorter vanilla visibility limits.
@Mixin(FogRenderer.class)
abstract class DomainFogMixin {
    @Keep
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void cursedOath$shrineFog(
            Camera camera,
            int unusedDistance,
            DeltaTracker delta,
            float unusedDarkness,
            ClientLevel level,
            CallbackInfoReturnable<FogData> callback) {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        float intensity = 0;
        for (var domain : DomainIndex.inLevel(level)) {
            if (domain.isRemoved() || !domain.contains(camera.position())) continue;
            if (domain.closed()) return;
            float edge =
                    (float) Math.clamp((domain.radius() - camera.position().distanceTo(domain.position())) / 4, 0, 1);
            float reveal = Math.clamp(
                    (level.getGameTime() - domain.started() + delta.getGameTimeDeltaPartialTick(false)) / 24f, 0, 1);
            intensity = Math.max(intensity, edge * reveal);
        }
        if (intensity == 0) return;
        var fog = callback.getReturnValue();
        float blend = intensity * 0.8f;
        fog.color.set(
                fog.color.x * (1 - blend) + 0.28f * blend,
                fog.color.y * (1 - blend) + 0.025f * blend,
                fog.color.z * (1 - blend) + 0.035f * blend,
                fog.color.w);
        fog.environmentalStart = Math.min(fog.environmentalStart, 24);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, 256 / intensity);
        fog.skyEnd = Math.min(fog.skyEnd, 64 / intensity);
    }
}
