package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.Defense;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/// Collects pressured Simple Domains for one server-wide batch; each loses strength at most once per tick.
final class DomainProtection {

    private final Set<Defense> eroding = new LinkedHashSet<>();

    void finishTick() {
        eroding.forEach(Defense::erode);
        eroding.clear();
    }

    static boolean protects(LivingEntity target) {
        return protects(target, null);
    }

    /// Records every protecting field for deferred erosion, without weakening it during target iteration.
    boolean intercept(LivingEntity target) {
        return protects(target, eroding);
    }

    private static boolean protects(LivingEntity target, @Nullable Set<Defense> eroding) {
        if (!(target.level() instanceof ServerLevel level)) return false;
        boolean protectedTarget = false;
        for (var fighter : CombatRuntime.fighters(level)) {
            if (fighter.defense().simple() > 0
                    && fighter.player().isAlive()
                    && fighter.player().distanceToSqr(target) <= Defense.SIMPLE_RADIUS_SQUARED) {
                if (eroding == null) return true;
                protectedTarget = true;
                eroding.add(fighter.defense());
            }
        }
        return protectedTarget;
    }
}
