package io.github.romeoahmed.cursedoath.client.animation

import com.zigythebird.playeranim.animation.PlayerAnimationController
import com.zigythebird.playeranim.api.PlayerAnimationAccess
import com.zigythebird.playeranim.api.PlayerAnimationFactory
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode
import com.zigythebird.playeranimcore.enums.PlayState
import io.github.romeoahmed.cursedoath.CursedOath
import io.github.romeoahmed.cursedoath.technique.Technique
import net.minecraft.world.entity.Avatar

/** Client-only PAL adapter; animation does not drive damage or movement. */
object CastingAnimation {
    private const val LAYER_PRIORITY = 1500
    private val layer = CursedOath.id("casting")

    fun initialize() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(layer, LAYER_PRIORITY) { player ->
            PlayerAnimationController(player) { _, _, _ -> PlayState.STOP }.apply {
                setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
            }
        }
    }

    fun start(
        player: Avatar,
        technique: Technique,
        release: Boolean,
        elapsedTicks: Float,
    ) {
        val name =
            when (technique) {
                Technique.BLUE -> "attract"
                Technique.RED -> "repel"
                Technique.PURPLE -> "fusion"
                Technique.DISMANTLE -> "dismantle"
                Technique.CLEAVE -> "cleave"
                Technique.HEAL -> "seal"
                Technique.INFINITY -> return
            }
        controller(player)?.triggerAnimation(CursedOath.id(if (release) "${name}_release" else name), elapsedTicks)
    }

    fun stop(player: Avatar) {
        controller(player)?.stopTriggeredAnimation()
    }

    private fun controller(player: Avatar) =
        PlayerAnimationAccess.getPlayerAnimationLayer(player, layer) as? PlayerAnimationController
}
