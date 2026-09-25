package io.github.romeoahmed.cursedoath.technique;

import io.github.romeoahmed.cursedoath.world.SweptVolume;
import java.util.ArrayList;
import java.util.List;
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
        var planes = new ArrayList<SweptVolume>();
        for (int line = -TechniqueTuning.CLEAVE_GRID; line <= TechniqueTuning.CLEAVE_GRID; line++) {
            double offset = line * TechniqueTuning.CLEAVE_SPACING;
            var a = center.add(side.scale(offset));
            var b = center.add(up.scale(offset));
            var motion = forward.scale(TechniqueTuning.CLEAVE_EXTENT);
            planes.add(new SweptVolume(
                    a,
                    a.add(motion),
                    new Vec3(
                            TechniqueTuning.CLEAVE_THICKNESS,
                            TechniqueTuning.CLEAVE_EXTENT,
                            TechniqueTuning.CLEAVE_THICKNESS)));
            planes.add(new SweptVolume(
                    b,
                    b.add(motion),
                    new Vec3(
                            TechniqueTuning.CLEAVE_EXTENT,
                            TechniqueTuning.CLEAVE_THICKNESS,
                            TechniqueTuning.CLEAVE_THICKNESS)));
        }
        cuts = List.copyOf(planes);
        var box = cuts.getFirst().bounds();
        for (int i = 1; i < cuts.size(); i++) box = box.minmax(cuts.get(i).bounds());
        bounds = box;
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
