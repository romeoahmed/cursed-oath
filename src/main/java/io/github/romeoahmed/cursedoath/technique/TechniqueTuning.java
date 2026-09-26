package io.github.romeoahmed.cursedoath.technique;

/// Game units: Red peak damage is twice the sum of Blue's six pulses.
public final class TechniqueTuning {
    private TechniqueTuning() {}

    public static final int BLUE_DURATION = 60;
    public static final int BLUE_INTERVAL = 10;
    public static final float BLUE_DAMAGE = 8f;
    public static final float BLUE_OUTPUT = BLUE_DAMAGE * (BLUE_DURATION / BLUE_INTERVAL);
    public static final double BLUE_RADIUS = 12.0;
    public static final double BLUE_CORE = 3.5;
    public static final double BLUE_EXCAVATION = 6.0;
    public static final float RED_DAMAGE = BLUE_OUTPUT * 2;
    public static final double RED_RADIUS = 10.0;
    public static final double RED_EXCAVATION = 8.0;
    public static final float PURPLE_DAMAGE = 360f;
    public static final double PURPLE_RADIUS = 6.0;
    public static final double PURPLE_RANGE = 128.0;
    public static final double PURPLE_SPEED = 4.0;
    public static final float DISMANTLE_DAMAGE = 36f;
    public static final double DISMANTLE_WIDTH = 5.0;
    public static final double DISMANTLE_RANGE = 48.0;
    public static final float CLEAVE_DAMAGE = 64f;
    public static final float CLEAVE_MAX_DAMAGE = 160f;
    public static final int CLEAVE_GRID = 3;
    public static final double CLEAVE_SPACING = 2.0;
    public static final double CLEAVE_THICKNESS = 0.06;
    public static final double CLEAVE_EXTENT = 7.0;

    public static double launchDistance(Technique technique) {
        return switch (technique) {
            case PURPLE -> 4.0;
            case BLUE, RED -> 1.2;
            default -> 0.0;
        };
    }

    public static final float FUSION_CONTACT = 0.65f;
}
