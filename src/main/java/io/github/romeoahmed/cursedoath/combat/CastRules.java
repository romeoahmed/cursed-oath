package io.github.romeoahmed.cursedoath.combat;

import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class CastRules {
    private CastRules() {}

    public static boolean qualified(ServerPlayer player, Technique technique) {
        var profile = player.getAttachedOrCreate(SorcererAttachments.PROFILE);
        if (technique.domain()) return profile.barriers();
        if (technique == Technique.HEAL) return profile.selfHealing();
        return !technique.requiresReversal() || profile.reversal();
    }

    public static boolean available(ServerPlayer player, Technique technique) {
        if (!player.isAlive() || player.isSpectator() || Domains.isOverloaded(player)) return false;
        return technique.domain()
                || (!player.isUsingItem() && !player.isPassenger() && !player.isFallFlying() && !player.isSwimming());
    }

    static @Nullable String rejection(Fighter fighter, Technique technique) {
        var player = fighter.player();
        if (!qualified(player, technique)) return "qualification";
        if (Domains.isOverloaded(player)) return "overloaded";
        if (technique.innate()) {
            if (fighter.defense().amplification()) return "amplification";
            if (fighter.burnout() > 0 && Domains.ownedBy(player) == null) return "burnout";
        }
        if (fighter.cast() != null || fighter.recovery() > 0) return "busy";
        if (!available(player, technique)) return "hands";
        if (technique == Technique.HEAL && player.getHealth() >= player.getMaxHealth()) return "healthy";
        if (technique == Technique.INFINITY && fighter.energy().available() < Defense.MAINTENANCE) return "energy";
        return null;
    }
}
