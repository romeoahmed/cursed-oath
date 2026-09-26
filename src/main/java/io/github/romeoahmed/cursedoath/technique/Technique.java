package io.github.romeoahmed.cursedoath.technique;

import com.google.errorprone.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/// Ability metadata with explicit wire IDs and translation paths independent of declaration order.
public enum Technique {
    BLUE(1, "limitless.blue", new Cost(15, 85), 12, 16, 0x39BBFF),
    RED(2, "limitless.red", new Cost(25, 125), 18, 24, 0xFF101C),
    DISMANTLE(3, "shrine.dismantle", new Cost(10, 40), 6, 10, 0xE9E7DC),
    CLEAVE(4, "shrine.cleave", new Cost(10, 65), 5, 12, 0xFFB9A9),
    HEAL(5, "reverse_cursed_technique", new Cost(10, 140), 20, 30, 0x96F9C9),
    PURPLE(7, "limitless.purple", new Cost(80, 420), 50, 60, 0xB268FF),
    UNLIMITED_VOID(8, "limitless.unlimited_void", new Cost(50, 550), 30, 0, 0x719BFF),
    MALEVOLENT_SHRINE(9, "shrine.malevolent_shrine", new Cost(50, 550), 30, 0, 0xE65B4C),
    SIMPLE_DOMAIN(10, "simple_domain", new Cost(0, 60), 0, 0, 0xD8F1F8),
    AMPLIFICATION(11, "domain_amplification", new Cost(0, 0), 0, 0, 0xDDD9E8),
    INFINITY(6, "limitless.infinity", new Cost(0, 0), 0, 0, 0xB1ECFF),
    ;

    @Immutable
    public record Cost(int startup, int release) {}

    private final int wireId;
    private final String path;
    private final Cost cost;
    private final int preparation;
    private final int recovery;
    private final int color;

    Technique(int wireId, String path, Cost cost, int preparation, int recovery, int color) {
        this.wireId = wireId;
        this.path = path;
        this.cost = cost;
        this.preparation = preparation;
        this.recovery = recovery;
        this.color = color;
    }

    public int wireId() {
        return wireId;
    }

    public String path() {
        return path;
    }

    public Cost cost() {
        return cost;
    }

    public int preparation() {
        return preparation;
    }

    public int recovery() {
        return recovery;
    }

    public int color() {
        return color;
    }

    public boolean domain() {
        return this == UNLIMITED_VOID || this == MALEVOLENT_SHRINE;
    }

    public boolean innate() {
        return this != HEAL && this != SIMPLE_DOMAIN && this != AMPLIFICATION;
    }

    public boolean destroysTerrain() {
        return switch (this) {
            case BLUE, RED, DISMANTLE, CLEAVE, PURPLE -> true;
            default -> false;
        };
    }

    public boolean requiresReversal() {
        return this == RED || this == PURPLE;
    }

    public String translationKey() {
        return "ability.cursed-oath." + path;
    }

    public static @Nullable Technique fromWire(int id) {
        return switch (id) {
            case 1 -> BLUE;
            case 2 -> RED;
            case 3 -> DISMANTLE;
            case 4 -> CLEAVE;
            case 5 -> HEAL;
            case 6 -> INFINITY;
            case 7 -> PURPLE;
            case 8 -> UNLIMITED_VOID;
            case 9 -> MALEVOLENT_SHRINE;
            case 10 -> SIMPLE_DOMAIN;
            case 11 -> AMPLIFICATION;
            default -> null;
        };
    }
}
