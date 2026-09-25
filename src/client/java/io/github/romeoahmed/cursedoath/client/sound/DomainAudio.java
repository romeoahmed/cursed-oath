package io.github.romeoahmed.cursedoath.client.sound;

import io.github.romeoahmed.cursedoath.domain.DomainIndex;
import io.github.romeoahmed.cursedoath.domain.DomainSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundSource;

/// Local slash ambience follows domain time rather than individual visual cuts.
public final class DomainAudio {
    private static final long INTERVAL = 5;
    private static final double DISTANCE = 8, PHASE = 2.399963;
    private static final float VOLUME = 0.22f, PITCH = 0.85f, PITCH_STEP = 0.1f;
    private static final int PITCH_VARIANTS = 4;

    private DomainAudio() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var level = client.level;
            var viewer = client.getCameraEntity();
            if (level == null || viewer == null || client.isPaused()) return;
            for (var domain : DomainIndex.inLevel(level)) {
                if (domain.isRemoved() || domain.closed() || !domain.contains(viewer.getEyePosition())) continue;
                long age = level.getGameTime() - domain.started();
                if (age % INTERVAL == 0) {
                    double phase = (age / (double) INTERVAL) * PHASE;
                    level.playLocalSound(
                            viewer.getX() + Math.cos(phase) * DISTANCE,
                            viewer.getEyeY(),
                            viewer.getZ() + Math.sin(phase) * DISTANCE,
                            DomainSounds.CUT,
                            SoundSource.HOSTILE,
                            VOLUME,
                            PITCH + (age / INTERVAL % PITCH_VARIANTS) * PITCH_STEP,
                            false);
                }
                break;
            }
        });
    }
}
