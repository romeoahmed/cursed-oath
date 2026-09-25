package io.github.romeoahmed.cursedoath.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.romeoahmed.cursedoath.CursedOath;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class EffectRenderTypes {
    // Rings and slashes need visible back faces.
    private static final RenderPipeline.Snippet SURFACE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withCull(false)
            .buildSnippet();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(SURFACE)
            .withLocation(CursedOath.id("pipeline/technique"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build());
    private static final OitPipelineSet OIT = RenderPipelines.register(OitPipelineSet.builder(
                    "technique", RenderPipeline.builder(SURFACE).withShaderDefine("OIT_ADDITIVE"))
            .withDepthBoundsModifier(builder -> builder.withLocation(CursedOath.id("pipeline/technique_depth_bounds")))
            .withTransmittanceModifier(
                    builder -> builder.withLocation(CursedOath.id("pipeline/technique_transmittance")))
            .withAccumulateModifier(builder -> builder.withLocation(CursedOath.id("pipeline/technique_accumulate")))
            .build());
    private static final RenderPipeline SOLID_PIPELINE =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(CursedOath.id("pipeline/domain_solid"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build());
    private static final RenderPipeline VOID_PIPELINE =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(CursedOath.id("pipeline/void"))
                    .withVertexShader("core/position_tex_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withFragmentShader(CursedOath.id("core/void"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build());
    public static final RenderType SOLID = RenderType.create(
            "cursed-oath:domain_solid", RenderSetup.builder(SOLID_PIPELINE).createRenderSetup());
    public static final RenderType VOID = RenderType.create(
            "cursed-oath:void",
            RenderSetup.builder(VOID_PIPELINE)
                    .withTexture("Sampler0", CursedOath.id("textures/environment/void.png"))
                    .createRenderSetup());
    public static final RenderType CORE = RenderTypes.debugQuads();
    public static final RenderType ADDITIVE = RenderType.create(
            "cursed-oath:technique",
            RenderSetup.builder(PIPELINE).setOitPipelines(OIT).sortOnUpload().createRenderSetup());

    private EffectRenderTypes() {}
}
