package io.github.romeoahmed.cursedoath.combat;

/// Pre-mitigation melee scaling; the two-unit Black Flash minimum lets unarmed strikes benefit.
public final class MeleeDamage {
    private MeleeDamage() {}

    public static float resolve(float damage, boolean blackFlash) {
        if (damage <= 0) return 0;
        return blackFlash ? (float) Math.pow(Math.max(damage, 2f), 2.5) : damage * 1.15f;
    }
}
