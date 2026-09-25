package io.github.romeoahmed.cursedoath.technique

/** Wire IDs and translation paths remain stable when entries are reordered. */
enum class Technique(
    val wireId: Int,
    val path: String,
    val cost: CastCost,
    val preparation: Int,
    val recovery: Int,
    val color: Int,
) {
    BLUE(1, "limitless.blue", CastCost(15, 85), 12, 16, 0x39BBFF),
    RED(2, "limitless.red", CastCost(25, 125), 18, 24, 0xFF101C),
    DISMANTLE(3, "shrine.dismantle", CastCost(10, 40), 6, 10, 0xE9E7DC),
    CLEAVE(4, "shrine.cleave", CastCost(10, 65), 5, 12, 0xFFB9A9),
    HEAL(5, "reverse_cursed_technique", CastCost(10, 140), 20, 30, 0x96F9C9),
    PURPLE(7, "limitless.purple", CastCost(80, 420), 50, 60, 0xB268FF),
    UNLIMITED_VOID(8, "limitless.unlimited_void", CastCost(50, 550), 30, 0, 0x719BFF),
    MALEVOLENT_SHRINE(9, "shrine.malevolent_shrine", CastCost(50, 550), 30, 0, 0xE65B4C),
    SIMPLE_DOMAIN(10, "simple_domain", CastCost(0, 60), 0, 0, 0xD8F1F8),
    AMPLIFICATION(11, "domain_amplification", CastCost(0, 0), 0, 0, 0xDDD9E8),
    INFINITY(6, "limitless.infinity", CastCost(0, 0), 0, 0, 0xB1ECFF),
    ;

    val domain: Boolean get() = this == UNLIMITED_VOID || this == MALEVOLENT_SHRINE
    val innate: Boolean get() = this != HEAL && this != SIMPLE_DOMAIN && this != AMPLIFICATION

    val destroysTerrain: Boolean get() =
        this == BLUE || this == RED || this == DISMANTLE || this == CLEAVE ||
            this == PURPLE

    val requiresReversal: Boolean get() = this == RED || this == PURPLE

    val translationKey: String get() = "ability.cursed-oath.$path"

    companion object {
        fun fromWire(id: Int): Technique? = entries.firstOrNull { it.wireId == id }
    }
}

data class CastCost(
    val startup: Int,
    val release: Int,
)
