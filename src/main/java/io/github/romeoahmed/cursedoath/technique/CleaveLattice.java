package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.geometry.SweptVolume;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The same finite cutting planes drive terrain, entity contact and the client grid.
public final class CleaveLattice {
    private final Vec3 center;
    private final List<SweptVolume> cuts;
    private final AABB bounds;

    public CleaveLattice(Vec3 center, Vec3 direction) {
        this.center = center;
        var forward = direction.normalize();
        var reference = Math.abs(forward.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        var side = forward.cross(reference).normalize();
        var up = side.cross(forward).normalize();
        var motion = forward.scale(TechniqueTuning.CLEAVE_EXTENT);
        var vertical = new Vec3(
                TechniqueTuning.CLEAVE_THICKNESS, TechniqueTuning.CLEAVE_EXTENT, TechniqueTuning.CLEAVE_THICKNESS);
        var horizontal = new Vec3(
                TechniqueTuning.CLEAVE_EXTENT, TechniqueTuning.CLEAVE_THICKNESS, TechniqueTuning.CLEAVE_THICKNESS);
        cuts = IntStream.rangeClosed(-TechniqueTuning.CLEAVE_GRID, TechniqueTuning.CLEAVE_GRID)
                .boxed()
                .<SweptVolume>mapMulti((line, emit) -> {
                    double offset = line * TechniqueTuning.CLEAVE_SPACING;
                    var a = center.add(side.scale(offset));
                    var b = center.add(up.scale(offset));
                    emit.accept(new SweptVolume(a, a.add(motion), vertical));
                    emit.accept(new SweptVolume(b, b.add(motion), horizontal));
                })
                .toList();
        bounds = cuts.stream().map(SweptVolume::bounds).reduce(AABB::minmax).orElseThrow();
    }

    public Vec3 center() {
        return center;
    }

    public List<SweptVolume> cuts() {
        return cuts;
    }

    public AABB bounds() {
        return bounds;
    }
}
