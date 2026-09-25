package io.github.romeoahmed.cursedoath.client.sound

import io.github.romeoahmed.cursedoath.domain.DomainSounds
import io.github.romeoahmed.cursedoath.domain.Domains
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.sounds.SoundSource
import kotlin.math.cos
import kotlin.math.sin

/** Local slash ambience follows domain time rather than individual visual cuts. */
object DomainAudio {
    private const val INTERVAL = 5L
    private const val DISTANCE = 8.0
    private const val PHASE = 2.399963
    private const val VOLUME = 0.22f
    private const val PITCH = 0.85f
    private const val PITCH_VARIANTS = 4
    private const val PITCH_STEP = 0.1f

    fun initialize() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val level = client.level
            val viewer = client.cameraEntity
            if (level != null && viewer != null && !client.isPaused) {
                val domain = Domains.inLevel(level).firstOrNull { !it.closed && it.contains(viewer.eyePosition) }
                if (domain != null) {
                    val age = level.gameTime - domain.started
                    if (age % INTERVAL == 0L) {
                        val phase = age / INTERVAL * PHASE
                        level.playLocalSound(
                            viewer.x + cos(phase) * DISTANCE,
                            viewer.eyeY,
                            viewer.z + sin(phase) * DISTANCE,
                            DomainSounds.CUT,
                            SoundSource.HOSTILE,
                            VOLUME,
                            PITCH + (age / INTERVAL % PITCH_VARIANTS) * PITCH_STEP,
                            false,
                        )
                    }
                }
            }
        }
    }
}
