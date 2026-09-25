package io.github.romeoahmed.cursedoath.technique;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class CleaveLatticeTest {
    @Test
    void latticeCoversEveryLineSymmetricallyAndPreservesGaps() {
        var grid = new CleaveLattice(Vec3.ZERO, new Vec3(0.0, 0.0, 1.0));

        Predicate<AABB> intersects = box -> grid.cuts().stream().anyMatch(cut -> cut.entry(box) != null);

        for (int offset = -6; offset <= 6; offset += 2) {
            assertTrue(intersects.test(AABB.ofSize(new Vec3(offset, 1.0, 4.0), 0.1, 0.1, 0.1)));
            assertTrue(intersects.test(AABB.ofSize(new Vec3(1.0, offset, 4.0), 0.1, 0.1, 0.1)));
        }
        assertTrue(!intersects.test(AABB.ofSize(new Vec3(0.0, 0.0, 10.0), 0.1, 0.1, 0.1)));
        assertTrue(!intersects.test(AABB.ofSize(new Vec3(1.0, 1.0, 4.0), 0.5, 0.5, 0.5)));
        assertTrue(!intersects.test(AABB.ofSize(new Vec3(8.0, 0.0, 4.0), 0.5, 0.5, 0.5)));
    }
}
