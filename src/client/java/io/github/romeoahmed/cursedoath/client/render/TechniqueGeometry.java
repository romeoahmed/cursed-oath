package io.github.romeoahmed.cursedoath.client.render;

import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import net.minecraft.world.phys.Vec3;

final class TechniqueGeometry {
    private static final double HEAL_RADIUS = 0.6, DETAIL_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final int POINTS = 17;
    private static final int GRID = TechniqueTuning.CLEAVE_GRID;
    private static final double SPACING = TechniqueTuning.CLEAVE_SPACING;
    private static final double EDGE_WIDTH = 0.22;
    private static final double BLADE_BOW = 0.9;
    private static final double WAKE_LENGTH = 0.65;
    private static final float WAKE_ALPHA = 0.1f;
    private static final int REVEAL_SPEED = 5;
    private static final double FLASH_EXTENT = 2.0;
    private static final double BEND = 0.45;
    private static final int WHITE = 0xDFDCE1;
    private static final int CRIMSON = 0xBA0827;
    private static final int BLACK = 0x090308;
    private static final double FLASH_CORE_WIDTH = 0.08;
    private static final double FLASH_HALO_WIDTH = 0.2;
    private static final Vec3[] BLADE = new Vec3[POINTS];

    static {
        for (int i = 0; i < POINTS; i++) {
            double t = (double) i / (POINTS - 1);
            double taper = Math.sin(t * Math.PI);
            BLADE[i] = new Vec3((t * 2 - 1) * TechniqueTuning.DISMANTLE_WIDTH, taper, taper * BLADE_BOW);
        }
    }

    private final EffectMesh mesh;

    TechniqueGeometry(EffectMesh mesh) {
        this.mesh = mesh;
    }

    void draw(Technique technique, float progress, Vec3 direction) {
        switch (technique) {
            case DISMANTLE -> dismantle(direction, progress);
            case CLEAVE -> cleave(direction, progress);
            default -> mesh.sphere(Vec3.ZERO, HEAL_RADIUS + progress, technique.color(), 1 - progress);
        }
    }

    static int detail(double distanceSquared) {
        return distanceSquared > DETAIL_DISTANCE_SQUARED ? 2 : 1;
    }

    private void dismantle(Vec3 direction, float progress) {
        var axes = EffectMesh.basis(direction);
        var side = axes.side();
        var up = axes.up();
        var previous = side.scale(BLADE[0].x);
        var width = Vec3.ZERO;
        var edge = Vec3.ZERO;
        var wake = Vec3.ZERO;
        for (int i = 1; i < POINTS; i++) {
            var point = BLADE[i];
            var next = side.scale(point.x).add(direction.scale(point.z));
            var nextWidth = up.scale(EDGE_WIDTH * point.y);
            var nextEdge = up.scale(-0.025 * point.y);
            var nextWake = direction.scale(-WAKE_LENGTH * point.y);
            mesh.ribbon(previous, next, width, nextWidth, BLACK, 1 - progress);
            mesh.ribbon(previous, next, edge, nextEdge, WHITE, 0.65f * (1 - progress));
            mesh.ribbon(previous, next, wake, nextWake, BLACK, WAKE_ALPHA * (1 - progress));
            previous = next;
            width = nextWidth;
            edge = nextEdge;
            wake = nextWake;
        }
    }

    private void cleave(Vec3 direction, float progress) {
        var axes = EffectMesh.basis(direction);
        var side = axes.side();
        var up = axes.up();
        double extent = TechniqueTuning.CLEAVE_EXTENT;
        float fade = (1 - progress) * (1 - progress);
        for (int line = -GRID; line <= GRID; line++) {
            double offset = line * SPACING, span = extent * Math.min(progress * REVEAL_SPEED, 1f);
            var a = side.scale(offset).add(up.scale(-span));
            var b = side.scale(offset).add(up.scale(span));
            mesh.slash(a, b, side.scale(EDGE_WIDTH), BLACK, fade);
            mesh.slash(a, b, side.scale(0.025), WHITE, fade * 0.45f);
            var c = up.scale(offset).add(side.scale(-span));
            var d = up.scale(offset).add(side.scale(span));
            mesh.slash(c, d, up.scale(EDGE_WIDTH), BLACK, fade);
            mesh.slash(c, d, up.scale(0.025), WHITE, fade * 0.45f);
        }
    }

    void blackFlash(Vec3 direction, float progress) {
        blackFlash(direction, progress, false);
    }

    void blackFlash(Vec3 direction, float progress, boolean core) {
        var axes = EffectMesh.basis(direction);
        var side = axes.side();
        var up = axes.up();
        for (int i = -GRID; i <= GRID; i++) {
            var end = side.scale(i).add(up.scale(i % 2 == 0 ? FLASH_EXTENT : -FLASH_EXTENT));
            var width = up.scale(core ? FLASH_CORE_WIDTH : FLASH_HALO_WIDTH);
            var offset = width.scale(-0.5);
            mesh.ribbon(
                    new Vec3[] {offset, end.scale(BEND).add(side).add(offset), end.add(offset)},
                    width,
                    core ? BLACK : CRIMSON,
                    1 - progress);
        }
    }
}
