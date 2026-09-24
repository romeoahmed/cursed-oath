package io.github.romeoahmed.cursedoath.technique

/** Game units: Red peak damage is twice the sum of Blue's six pulses. */
object TechniqueTuning {
    const val BLUE_DURATION = 60
    const val BLUE_INTERVAL = 10
    const val BLUE_DAMAGE = 8f
    const val BLUE_OUTPUT = BLUE_DAMAGE * (BLUE_DURATION / BLUE_INTERVAL)
    const val BLUE_RADIUS = 12.0
    const val BLUE_CORE = 3.5
    const val BLUE_EXCAVATION = 6.0
    const val RED_DAMAGE = BLUE_OUTPUT * 2
    const val RED_RADIUS = 10.0
    const val RED_EXCAVATION = 8.0
    const val PURPLE_DAMAGE = 360f
    const val PURPLE_RADIUS = 6.0
    const val PURPLE_RANGE = 128.0
    const val PURPLE_SPEED = 4.0
    const val DISMANTLE_DAMAGE = 36f
    const val DISMANTLE_WIDTH = 5.0
    const val DISMANTLE_RANGE = 48.0
    const val CLEAVE_DAMAGE = 64f
    const val CLEAVE_MAX_DAMAGE = 160f
    const val CLEAVE_GRID = 3
    const val CLEAVE_SPACING = 2.0
    const val CLEAVE_THICKNESS = 0.06
    const val CLEAVE_EXTENT = 7.0
    const val FUSION_CONTACT = 0.65f
}
