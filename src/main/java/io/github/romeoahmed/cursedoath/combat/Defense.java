package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.domain.DomainSounds;
import io.github.romeoahmed.cursedoath.technique.InfinityDefense;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class Defense {
    public static final int MAINTENANCE = 2;
    public static final int SIMPLE_STRENGTH = 100;
    public static final double SIMPLE_RADIUS_SQUARED = 9.0;
    private final ServerPlayer player;
    private boolean infinity;
    private boolean amplification;
    private int simple;
    private long erodedAt = Long.MIN_VALUE;

    Defense(ServerPlayer player) {
        this.player = player;
    }

    public boolean infinity() {
        return infinity;
    }

    public void infinity(boolean value) {
        infinity = value;
    }

    public boolean amplification() {
        return amplification;
    }

    public int simple() {
        return simple;
    }

    public boolean sustained() {
        return infinity || amplification;
    }

    CursedEnergy toggle(Technique technique, CursedEnergy energy) {
        if (technique == Technique.SIMPLE_DOMAIN) {
            if (simple > 0) simple = 0;
            else {
                var paid = energy.spend(technique.cost().release());
                if (paid == null) return energy;
                simple = SIMPLE_STRENGTH;
                amplification = false;
                return paid;
            }
        } else {
            amplification = !amplification;
            if (amplification) {
                infinity = false;
                simple = 0;
            }
        }
        return energy;
    }

    CursedEnergy tick(CursedEnergy energy) {
        if (!sustained()) return energy;
        var paid = energy.spend(MAINTENANCE);
        if (paid == null) {
            clear();
            return energy;
        }
        if (infinity) InfinityDefense.interceptProjectiles(player);
        return paid;
    }

    public void erode() {
        long tick = player.level().getGameTime();
        if (erodedAt == tick) return;
        int previous = simple;
        simple = Math.max(simple - 1, 0);
        if (previous > 0 && simple == 0)
            player.level()
                    .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            DomainSounds.BREAK,
                            SoundSource.PLAYERS,
                            1f,
                            1f);
        erodedAt = tick;
    }

    void clear() {
        infinity = false;
        amplification = false;
        simple = 0;
    }
}
