package io.github.romeoahmed.cursedoath.client.render

import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.romeoahmed.cursedoath.CursedOath
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.oit.OitPipelineSet
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

internal object TechniqueRenderTypes {
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
