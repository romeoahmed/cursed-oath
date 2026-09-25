package io.github.romeoahmed.cursedoath.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.romeoahmed.cursedoath.CursedOath
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.oit.OitPipelineSet
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

internal object EffectRenderTypes {
    // Rings and slashes need visible back faces.
    private val surface = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET).withCull(false).buildSnippet()
    private val pipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(surface)
                .withLocation(CursedOath.id("pipeline/technique"))
                .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
                .build(),
        )
    private val oit =
        RenderPipelines.register(
            OitPipelineSet
                .builder("technique", RenderPipeline.builder(surface).withShaderDefine("OIT_ADDITIVE"))
                .withDepthBoundsModifier { it.withLocation(CursedOath.id("pipeline/technique_depth_bounds")) }
                .withTransmittanceModifier { it.withLocation(CursedOath.id("pipeline/technique_transmittance")) }
                .withAccumulateModifier { it.withLocation(CursedOath.id("pipeline/technique_accumulate")) }
                .build(),
        )

    private val solidPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(CursedOath.id("pipeline/domain_solid"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build(),
        )
    private val voidPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(CursedOath.id("pipeline/void"))
                .withVertexShader("core/position_tex_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withFragmentShader(CursedOath.id("core/void"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .build(),
        )
    val solid: RenderType =
        RenderType.create(
            "cursed-oath:domain_solid",
            RenderSetup.builder(solidPipeline).createRenderSetup(),
        )
    val void: RenderType =
        RenderType.create(
            "cursed-oath:void",
            RenderSetup
                .builder(voidPipeline)
                .withTexture("Sampler0", CursedOath.id("textures/environment/void.png"))
                .createRenderSetup(),
        )

    val core: RenderType = RenderTypes.debugQuads()

    val additive: RenderType =
        RenderType.create(
            "cursed-oath:technique",
            RenderSetup
                .builder(pipeline)
                .setOitPipelines(oit)
                .sortOnUpload()
                .createRenderSetup(),
        )
}
