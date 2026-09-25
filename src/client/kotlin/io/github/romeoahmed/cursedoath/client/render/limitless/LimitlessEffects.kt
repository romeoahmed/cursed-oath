package io.github.romeoahmed.cursedoath.client.render.limitless

import com.mojang.blaze3d.vertex.PoseStack
import io.github.romeoahmed.cursedoath.client.render.EffectMesh
import io.github.romeoahmed.cursedoath.client.render.EffectRenderTypes
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/** Immutable forms are shared by the depth-writing body and additive flow passes. */
internal object LimitlessEffects {
    data class Form(
        val technique: Technique,
        val center: Vec3,
        val direction: Vec3,
        val radius: Double,
        val age: Float,
        val flying: Boolean = false,
        val fusion: Float = 0f,
    )

    fun charge(
        technique: Technique,
        progress: Float,
        direction: Vec3,
    ): List<Form> {
        val age = progress * technique.preparation
        if (technique != Technique.PURPLE) {
            val radius = if (technique == Technique.BLUE) BLUE_CHARGE else RED_RADIUS
            return listOf(
                Form(technique, Vec3.ZERO, direction, radius * (CHARGE_START + progress * CHARGE_GROWTH), age),
            )
        }
        return fusion(progress, direction, age)
    }

    private fun fusion(
        progress: Float,
        direction: Vec3,
        age: Float,
    ): List<Form> {
        val contact = TechniqueTuning.FUSION_CONTACT
        val merging = smooth((progress - contact) / MERGE_DURATION)
        if (progress < contact + MERGE_DURATION) {
            val approach = smooth(progress / contact)
            val separation = (SEPARATION + (FUSION_RADIUS - SEPARATION) * approach) * (1 - merging)
            val offset = EffectMesh.basis(direction).first.scale(separation)
            val radius = FUSION_RADIUS + (MERGED_RADIUS - FUSION_RADIUS) * merging
            return listOf(
                Form(Technique.BLUE, offset, direction, radius, age, fusion = merging),
                Form(Technique.RED, offset.reverse(), direction, radius, age, fusion = merging),
            )
        }
        val growth = smooth((progress - contact - MERGE_DURATION) / (1 - contact - MERGE_DURATION))
        val radius = MERGED_RADIUS + (RELEASE_RADIUS - MERGED_RADIUS) * growth
        return listOf(Form(Technique.PURPLE, Vec3.ZERO, direction, radius, age))
    }

    fun flight(
        technique: Technique,
        age: Float,
        direction: Vec3,
    ): List<Form> {
        val radius =
            when (technique) {
                Technique.BLUE -> {
                    val fade = ((1 - age / TechniqueTuning.BLUE_DURATION) * FADE_SPEED).coerceIn(0f, 1f)
                    BLUE_RADIUS * fade
                }

                Technique.RED -> {
                    RED_RADIUS
                }

                Technique.PURPLE -> {
                    RELEASE_RADIUS + (TechniqueTuning.PURPLE_RADIUS - RELEASE_RADIUS) * smooth(age / RELEASE_TICKS)
                }

                else -> {
                    return emptyList()
                }
            }
        val center =
            if (technique == Technique.PURPLE) {
                direction.scale(CHARGE_DISTANCE * (1 - smooth(age / RELEASE_TICKS)))
            } else {
                Vec3.ZERO
            }
        // Keep surface motion continuous when the preparation event hands over to the projectile.
        val visualAge = if (technique == Technique.PURPLE) age + technique.preparation else age
        return listOf(Form(technique, center, direction, radius, visualAge, flying = true))
    }

    fun submit(
        pose: PoseStack,
        collector: SubmitNodeCollector,
        forms: List<Form>,
        opacity: Float = 1f,
    ) {
        if (forms.isEmpty()) return
        collector.submitCustomGeometry(pose, EffectRenderTypes.solid) { matrix, vertices ->
            val surface = EnergySurface(matrix, vertices)
            for (form in forms) surface.draw(form)
        }
        collector.submitCustomGeometry(pose, EffectRenderTypes.additive) { matrix, vertices ->
            val trails = EnergyTrails(matrix, vertices, opacity)
            for (form in forms) trails.draw(form)
        }
    }

    private fun smooth(value: Float): Float = Mth.smoothstep(value.coerceIn(0f, 1f))

    const val CHARGE_DISTANCE = 4.0
    const val VISUAL_RADIUS = 16.0
    private const val BLUE_RADIUS = 1.4
    private const val BLUE_CHARGE = 0.6
    private const val RED_RADIUS = 0.18
    private const val FUSION_RADIUS = 0.45
    private const val MERGED_RADIUS = 0.65
    private const val RELEASE_RADIUS = 1.6
    private const val MERGE_DURATION = 0.12f
    private const val RELEASE_TICKS = 4f
    private const val SEPARATION = 2.2
    private const val CHARGE_START = 0.4
    private const val CHARGE_GROWTH = 0.6
    private const val FADE_SPEED = 6
}
