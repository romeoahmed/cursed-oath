package io.github.romeoahmed.cursedoath.mixin;

import com.google.errorprone.annotations.Keep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.romeoahmed.cursedoath.client.render.domain.DomainRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Hides scenery for the extracted frame without changing world blocks or entity simulation.
@Mixin(LevelRenderer.class)
abstract class DomainEnvironmentMixin {
    @Unique
    private boolean cursedOath$void;

    @Keep
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void cursedOath$environment(
            LevelRenderState state,
            SubmitNodeCollector unusedCollector,
            boolean unusedOutline,
            CallbackInfo unusedCallback) {
        cursedOath$void = state.entityRenderStates.stream()
                .anyMatch(entity -> entity instanceof DomainRenderer.State domain
                        && domain.closed()
                        && state.cameraRenderState.pos.distanceToSqr(entity.x, entity.y, entity.z)
                                < domain.radius() * domain.radius());
        if (cursedOath$void) {
            state.blockEntityRenderStates.clear();
            state.blockBreakingRenderStates.clear();
            state.blockOutlineRenderState = null;
            state.weatherRenderState.reset();
            state.cloudColor = 0;
        }
    }

    @Keep
    @WrapOperation(
            method = {"executeSolid", "executeClassicTransparency"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void cursedOath$terrain(
            ChunkSectionsToRender chunks,
            ChunkSectionLayerGroup group,
            RenderPass pass,
            GpuSampler sampler,
            GpuTextureView atlas,
            boolean wireframe,
            Operation<Void> original) {
        if (!cursedOath$void) original.call(chunks, group, pass, sampler, atlas, wireframe);
    }

    @Keep
    @WrapOperation(
            method = "executeOit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderOit(Lcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void cursedOath$transparentTerrain(
            ChunkSectionsToRender chunks,
            GpuSampler sampler,
            OitStage stage,
            OitRenderPassProvider.Parameters parameters,
            GpuTextureView atlas,
            GpuTextureView lightmap,
            Operation<Void> original) {
        if (!cursedOath$void) original.call(chunks, sampler, stage, parameters, atlas, lightmap);
    }
}
