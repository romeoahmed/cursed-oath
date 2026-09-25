package io.github.romeoahmed.cursedoath.client.render;

import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import net.minecraft.world.phys.Vec3;

public final class StrikeGeometry {
    private static final int POINTS = 17;
    private static final int GRID = TechniqueTuning.CLEAVE_GRID;
    private static final double SPACING = TechniqueTuning.CLEAVE_SPACING;
    private static final double EDGE_WIDTH = TechniqueTuning.CLEAVE_THICKNESS;
    private static final double BLADE_BOW = 1.5;
    private static final double WAKE_LENGTH = 1.2;
    private static final float WAKE_ALPHA = 0.18f;
    private static final int REVEAL_SPEED = 5;
    private static final double FLASH_EXTENT = 2.0;
    private static final double BEND = 0.45;
    private static final int WHITE = 0xF2F6FF;
    private static final int PALE = 0x9BAABD;
    private static final int CRIMSON = 0xBA0827;
    private static final int BLACK = 0x090308;
    private static final double FLASH_CORE_WIDTH = 0.08;
    private static final double FLASH_HALO_WIDTH = 0.2;
    private final EffectMesh mesh;

    public StrikeGeometry(EffectMesh mesh) {
        this.mesh = mesh;
    }

    public void dismantle(Vec3 direction, float progress) {
        var axes = EffectMesh.basis(direction);
        var side = axes.side();
        var up = axes.up();
        double span = TechniqueTuning.DISMANTLE_WIDTH;
        var points = new Vec3[POINTS];
        for (int i = 0; i < POINTS; i++) {
            double t = (double) i / (POINTS - 1);
            points[i] = side.scale((t * 2 - 1) * span).add(direction.scale(Math.sin(t * Math.PI) * BLADE_BOW));
        }
        mesh.ribbon(points, up.scale(EDGE_WIDTH), WHITE, 1 - progress);
        mesh.ribbon(points, direction.scale(-WAKE_LENGTH).add(up.scale(EDGE_WIDTH)), PALE, WAKE_ALPHA * (1 - progress));
    }

    public void cleave(Vec3 direction, float progress) {
        var axes = EffectMesh.basis(direction);
        var side = axes.side();
        var up = axes.up();
        double extent = TechniqueTuning.CLEAVE_EXTENT;
        float fade = (1 - progress) * (1 - progress);
        for (int line = -GRID; line <= GRID; line++) {
            double offset = line * SPACING, span = extent * Math.min(progress * REVEAL_SPEED, 1f);
            var a = side.scale(offset).add(up.scale(-span));
            var b = side.scale(offset).add(up.scale(span));
            mesh.ribbon(a, b, side.scale(EDGE_WIDTH), WHITE, fade);
            var c = up.scale(offset).add(side.scale(-span));
            var d = up.scale(offset).add(side.scale(span));
            mesh.ribbon(c, d, up.scale(EDGE_WIDTH), WHITE, fade);
        }
    }

    public void blackFlash(Vec3 direction, float progress) {
        blackFlash(direction, progress, false);
    }

    public void blackFlash(Vec3 direction, float progress, boolean core) {
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
