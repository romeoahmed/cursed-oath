package io.github.romeoahmed.cursedoath.world;

import io.github.romeoahmed.cursedoath.geometry.SweptVolume;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Incremental block traversal that keeps scanning visible to the excavation budget.
/// Each `next()` consumes one visit: `null` means scan-only work, an immutable position means a commit candidate.
/// Candidates still require world-state and protection checks before removal.
final class ExcavationCursors {
    private ExcavationCursors() {}

    static Iterator<@Nullable BlockPos> cuts(List<SweptVolume> volumes, Vec3 origin) {
        var bounds = volumes.getFirst().bounds();
        for (int i = 1; i < volumes.size(); i++)
            bounds = bounds.minmax(volumes.get(i).bounds());
        return new CutCursor(BlockPos.betweenClosed(bounds).iterator(), List.copyOf(volumes), origin);
    }

    static Iterator<@Nullable BlockPos> select(Iterator<BlockPos> scan, Predicate<BlockPos> accepts) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return scan.hasNext();
            }

            @Override
            public @Nullable BlockPos next() {
                var pos = scan.next();
                return accepts.test(pos) ? pos.immutable() : null;
            }
        };
    }

    private static final class CutCursor implements Iterator<@Nullable BlockPos> {
        private final Iterator<BlockPos> scan;
        private final List<SweptVolume> volumes;
        private final Vec3 origin;
        private final PriorityQueue<Candidate> candidates =
                new PriorityQueue<>(Comparator.comparingDouble(Candidate::distance)
                        .thenComparingLong(value -> value.position().asLong()));

        private CutCursor(Iterator<BlockPos> scan, List<SweptVolume> volumes, Vec3 origin) {
            this.scan = scan;
            this.volumes = volumes;
            this.origin = origin;
        }

        @Override
        public boolean hasNext() {
            return scan.hasNext() || !candidates.isEmpty();
        }

        @Override
        public @Nullable BlockPos next() {
            if (scan.hasNext()) {
                var pos = scan.next();
                var box = new AABB(pos);
                for (var volume : volumes) {
                    if (volume.entry(box) != null) {
                        candidates.add(new Candidate(
                                pos.immutable(),
                                Vec3.atCenterOf(pos)
                                        .subtract(origin)
                                        .dot(volumes.getFirst().forward())));
                        break;
                    }
                }
                return null;
            }
            if (candidates.isEmpty()) throw new NoSuchElementException();
            return candidates.remove().position();
        }
    }

    private record Candidate(BlockPos position, double distance) {}
}
