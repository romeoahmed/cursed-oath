package io.github.romeoahmed.cursedoath.client.render

import io.github.romeoahmed.cursedoath.client.animation.CastingAnimation
import io.github.romeoahmed.cursedoath.network.TechniqueEvent
import io.github.romeoahmed.cursedoath.technique.Technique
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Avatar
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object TechniqueVisuals {
    private const val MAX_DEBRIS_FIELDS = 8
    private const val MAX_EFFECTS = 96
    private const val MAX_SEEN = 512
    private const val IMPACT_DURATION = 16
    private const val HAND_DISTANCE = 1.2
    private const val CHARGE_DISTANCE = 4.0
    private const val MAX_DISTANCE_SQUARED = 128.0 * 128.0
    private const val EFFECT_SIZE = 32.0

    private data class Effect(
        val event: TechniqueEvent,
        val technique: Technique,
        val duration: Int,
    )

    private data class Shape(
        val position: Vec3,
        val direction: Vec3,
        val technique: Technique,
        val age: Float,
        val progress: Float,
        val stage: Int,
    )

    private val effects = ArrayList<Effect>()
    private val orbs = LinkedHashSet<TechniqueOrb>()
    private val seen = LinkedHashSet<Pair<UUID, Int>>()
    private val key = RenderStateDataKey.create<List<Shape>> { "cursed-oath:effects" }

    fun initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(::tickDebris)
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> clear() }
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ -> if (entity is TechniqueOrb) orbs.add(entity) }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> if (entity is TechniqueOrb) orbs.remove(entity) }
        val renderType = TechniqueRenderTypes.additive
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            val partial = context.deltaTracker().getGameTimeDeltaPartialTick(false)
            val time = context.level().gameTime.toDouble() + partial
            effects.removeAll {
                it.event.dimension != context.level().dimension().identifier() || time - it.event.tick > it.duration
            }
            val shapes =
                effects.map { effect -> shape(effect, context.level(), time, partial) }
            context.levelState().setData(key, shapes)
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            val shapes = context.levelState().getData(key) ?: return@register
            val cameraState = context.levelState().cameraRenderState
            val camera = cameraState.pos
            for (shape in shapes) {
                if (shape.position.distanceToSqr(camera) > MAX_DISTANCE_SQUARED ||
                    !cameraState.cullFrustum.isVisible(
                        AABB.ofSize(shape.position, EFFECT_SIZE, EFFECT_SIZE, EFFECT_SIZE),
                    )
                ) {
                    continue
                }
                val pose = context.poseStack()
                pose.pushPose()
                pose.translate(shape.position.x - camera.x, shape.position.y - camera.y, shape.position.z - camera.z)
                context.submitNodeCollector().submitCustomGeometry(pose, renderType) { matrix, vertices ->
                    val mesh =
                        TechniqueGeometry(
                            matrix,
                            vertices,
                            detail = TechniqueGeometry.detail(shape.position.distanceToSqr(camera)),
                        )
                    if (shape.stage == TechniqueEvent.PREPARE) {
                        mesh.charge(shape.technique, shape.progress, shape.direction)
                    } else if (shape.stage == TechniqueEvent.BLACK_FLASH) {
                        ShrineGeometry(TechniqueMesh(matrix, vertices)).blackFlash(shape.direction, shape.progress)
                    } else {
                        mesh.draw(shape.technique, shape.age, shape.progress, shape.direction)
                    }
                }
                if (shape.stage == TechniqueEvent.IMPACT && shape.technique == Technique.RED) {
                    context.submitNodeCollector().submitCustomGeometry(
                        pose,
                        TechniqueRenderTypes.core,
                    ) { matrix, vertices ->
                        TechniqueGeometry(matrix, vertices).core(shape.technique, shape.progress)
                    }
                }
                pose.popPose()
            }
        }
    }

    private fun tickDebris(client: Minecraft) {
        var remaining = MAX_DEBRIS_FIELDS
        for (orb in orbs) {
            if (orb.technique == Technique.BLUE && TechniqueDebris.blue(client, orb.position())) {
                if (--remaining == 0) break
            }
        }
    }

    private fun shape(
        effect: Effect,
        level: ClientLevel,
        time: Double,
        partial: Float,
    ): Shape {
        val age = (time - effect.event.tick).coerceAtLeast(0.0).toFloat()
        val actor = if (effect.event.stage == TechniqueEvent.PREPARE) level.getEntity(effect.event.actor) else null
        val direction =
            actor?.getViewVector(partial) ?: effect.event.destination
                .subtract(effect.event.origin)
                .normalize()
        val distance = if (effect.technique == Technique.PURPLE) CHARGE_DISTANCE else HAND_DISTANCE
        val position = actor?.getEyePosition(partial)?.add(direction.scale(distance)) ?: effect.event.destination
        return Shape(
            position,
            direction,
            effect.technique,
            age,
            (age / effect.duration).coerceIn(0f, 1f),
            effect.event.stage,
        )
    }

    fun accept(event: TechniqueEvent) {
        val level = Minecraft.getInstance().level ?: return
        if (event.dimension != level.dimension().identifier()) return
        val technique = Technique.fromWire(event.technique) ?: return
        if (!seen.add(event.id to event.stage)) return
        while (seen.size > MAX_SEEN) seen.removeFirst()
        val age = (level.gameTime - event.tick).coerceAtLeast(0)
        val actor = level.getEntity(event.actor) as? Avatar
        when (event.stage) {
            TechniqueEvent.PREPARE -> {
                prepare(actor, event, technique, age)
            }

            TechniqueEvent.CANCEL, TechniqueEvent.RELEASE -> {
                if (actor != null) animateRelease(actor, event, technique, age)
                finishEvent(event, technique, age)
            }

            TechniqueEvent.IMPACT -> {
                if (technique == Technique.RED) addEffect(event, technique, age, IMPACT_DURATION)
            }

            TechniqueEvent.BLACK_FLASH -> {
                addEffect(event, technique, age, IMPACT_DURATION)
            }
        }
    }

    private fun prepare(
        actor: Avatar?,
        event: TechniqueEvent,
        technique: Technique,
        age: Long,
    ) {
        if (actor != null && age < technique.preparation) CastingAnimation.start(actor, technique, false, age.toFloat())
        if (technique == Technique.PURPLE || technique == Technique.BLUE || technique == Technique.RED) {
            addEffect(event, technique, age, technique.preparation)
        }
    }

    private fun animateRelease(
        actor: Avatar,
        event: TechniqueEvent,
        technique: Technique,
        age: Long,
    ) {
        CastingAnimation.stop(actor)
        if (event.stage == TechniqueEvent.RELEASE && age < IMPACT_DURATION) {
            CastingAnimation.start(actor, technique, true, age.toFloat())
        }
    }

    private fun finishEvent(
        event: TechniqueEvent,
        technique: Technique,
        age: Long,
    ) {
        effects.removeAll { it.event.id == event.id && it.event.stage == TechniqueEvent.PREPARE }
        if (event.stage == TechniqueEvent.RELEASE && (technique == Technique.CLEAVE || technique == Technique.HEAL)) {
            addEffect(event, technique, age, IMPACT_DURATION)
        }
    }

    private fun addEffect(
        event: TechniqueEvent,
        technique: Technique,
        age: Long,
        duration: Int,
    ) {
        if (age >= duration) return
        if (effects.size == MAX_EFFECTS) effects.removeFirst()
        effects.add(Effect(event, technique, duration))
    }

    fun clear() {
        orbs.clear()
        effects.clear()
        seen.clear()
    }
}
