package io.github.romeoahmed.cursedoath.domain;

import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.Defense;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

final class DomainProtection {
    private DomainProtection() {}

    private static final Set<Defense> ERODING = new LinkedHashSet<>();

    static void finishTick() {
        ERODING.forEach(Defense::erode);
        ERODING.clear();
    }

    static boolean protects(LivingEntity target) {
        return protects(target, false);
    }

    static boolean protects(LivingEntity target, boolean erode) {
        if (!(target.level() instanceof ServerLevel level)) return false;
        boolean protectedTarget = false;
        for (var fighter : CombatRuntime.fighters(level)) {
            if (fighter.defense().simple() > 0
                    && fighter.player().isAlive()
                    && fighter.player().distanceToSqr(target) <= Defense.SIMPLE_RADIUS_SQUARED) {
                if (!erode) return true;
                protectedTarget = true;
                ERODING.add(fighter.defense());
            }
        }
        return protectedTarget;
    }
}
