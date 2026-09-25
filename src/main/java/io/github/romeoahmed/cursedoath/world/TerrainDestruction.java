package io.github.romeoahmed.cursedoath.world;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Bounded, round-robin excavation. Reservations include casts still preparing.
public final class TerrainDestruction {
    private TerrainDestruction() {}

    private static final int MAX_WORK = 16;
    private static final int CANDIDATES_PER_TICK = 16384;
    public static final int WRITES_PER_TICK = 1024;
    private static final int SLICE = 16;
    private static final long NANOS_PER_TICK = 4_000_000L;
    private static final long LIFETIME = 600;
    private static final int MAX_SEGMENTS = 64;
    private static final ArrayDeque<Work> QUEUE = new ArrayDeque<>();

    /// A terrain reservation owned by one server level and advanced on its server thread.
    /// Closing cancels queued work and callbacks; [#seal()] instead drains submitted piercing segments.
    public static final class Work implements AutoCloseable {
        private final ServerPlayer owner;
        private final ServerLevel level;
        private final long expires;
        private @Nullable Iterator<@Nullable BlockPos> positions;
        private final ArrayDeque<Iterator<@Nullable BlockPos>> pending = new ArrayDeque<>();
        private boolean released;
        private boolean finished;
        private boolean persistent;
        private @Nullable Runnable onComplete;
        private @Nullable Function<Vec3, Vec3> source;

        private Work(ServerPlayer owner) {
            this.owner = owner;
            level = owner.level();
            expires = level.getGameTime() + LIFETIME;
        }

        public ServerPlayer owner() {
            return owner;
        }

        public boolean finished() {
            return finished;
        }

        public boolean ready() {
            return positions == null && pending.isEmpty();
        }

        /// Registers a callback for the current cursor's completion; closing cancels it.
        ///
        /// @param action server-thread callback, which may schedule the next cut
        public void onComplete(Runnable action) {
            onComplete = action;
        }

        public void persistent(boolean value) {
            persistent = value;
        }

        public void cuts(List<SweptVolume> volumes) {
            cuts(volumes, volumes.getFirst().start());
        }

        public void cuts(List<SweptVolume> volumes, Vec3 origin) {
            requireReady();
            var first = volumes.getFirst();
            var bounds = first.bounds();
            for (int i = 1; i < volumes.size(); i++)
                bounds = bounds.minmax(volumes.get(i).bounds());
            source = point -> first.source(point, origin);
            positions = new CutCursor(BlockPos.betweenClosed(bounds).iterator(), List.copyOf(volumes), origin);
        }
        /// Queues a piercing segment without sorting or cover rays, excluding previously swept blocks.
        /// Submitted segments survive owner loss and the ordinary reservation timeout.
        ///
        /// @param volume segment to excavate
        /// @param previous earlier sweep to exclude, or `null` for the first segment
        /// @throws IllegalStateException if this work is closed or its pending queue is full
        public void pierce(SweptVolume volume, @Nullable SweptVolume previous) {
            if (finished || pending.size() >= MAX_SEGMENTS)
                throw new IllegalStateException("Terrain segment capacity exceeded");
            released = true;
            persistent = true;
            pending.addLast(mapPositions(BlockPos.betweenClosed(volume.bounds()).iterator(), pos -> {
                var box = new AABB(pos);
                return volume.entry(box) != null && (previous == null || previous.entry(box) == null);
            }));
        }

        /// Ends piercing submissions, retaining queued segments until they drain.
        public void seal() {
            persistent = false;
            if (ready()) close();
        }

        public void sphere(Vec3 center, double radius) {
            requireReady();
            source = null;
            int extent = (int) Math.ceil(radius);
            positions = mapPositions(
                    BlockPos.withinBoxByManhattanDistance(BlockPos.containing(center), extent, extent, extent)
                            .iterator(),
                    pos -> new AABB(pos).distanceToSqr(center) < radius * radius);
        }
        /// Starts outward Shrine excavation under the shared budget, without item drops.
        ///
        /// @param center fixed domain center
        /// @param radius excavation radius in blocks
        /// @throws IllegalStateException if this work is closed or still has queued positions
        public void domainCuts(Vec3 center, double radius) {
            sphere(center, radius);
            released = true;
            persistent = true;
        }

        @Override
        public void close() {
            finished = true;
            positions = null;
            pending.clear();
            onComplete = null;
            source = null;
        }

        private void requireReady() {
            if (!ready() || finished) throw new IllegalStateException("Terrain work is not ready");
        }

        @SuppressWarnings("ReferenceEquality") // Pending work belongs to one live world instance.
        private boolean validOwner() {
            return released
                    || (owner.level() == level && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator());
        }

        boolean advance() {
            if (!validOwner() || (!released && level.getGameTime() >= expires)) {
                close();
                return false;
            }
            var cursor = positions;
            if (cursor == null) {
                cursor = pending.pollFirst();
                positions = cursor;
            }
            if (cursor == null) return false;
            if (!cursor.hasNext()) {
                positions = null;
                var complete = onComplete;
                onComplete = null;
                if (!persistent && pending.isEmpty()) close();
                if (complete != null) complete.run();
                return false;
            }
            var pos = cursor.next();
            return pos != null && excavate(pos);
        }

        private boolean excavate(BlockPos pos) {
            if (level.getChunkSource()
                            .getChunkNow(
                                    SectionPos.blockToSectionCoord(pos.getX()),
                                    SectionPos.blockToSectionCoord(pos.getZ()))
                    == null) {
                if (!released) close();
                return false;
            }
            if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.isAir() || protectedBlock(state, pos)) return false;
            var ray = source;
            if (ray != null && !exposed(ray.apply(Vec3.atCenterOf(pos)), pos)) return false;
            return breakBlock(pos, state);
        }

        private boolean exposed(Vec3 origin, BlockPos pos) {
            if (!LoadedChunks.contains(level, new AABB(origin, Vec3.atCenterOf(pos)))) {
                close();
                return false;
            }
            var hit = level.clipIncludingBorder(new ClipContext(
                    origin, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, owner));
            return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
        }

        private boolean protectedBlock(BlockState state, BlockPos pos) {
            return state.hasBlockEntity()
                    || state.getDestroySpeed(level, pos) < 0
                    || !state.getFluidState().isEmpty()
                    || !level.mayInteract(owner, pos)
                    || owner.blockActionRestricted(level, pos, owner.gameMode.getGameModeForPlayer());
        }

        private boolean breakBlock(BlockPos pos, BlockState state) {
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, owner, pos, state, null)) {
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, owner, pos, state, null);
                return false;
            }
            // A callback may cancel the work or replace the block before this removal commits.
            if (finished || !validOwner() || !level.getBlockState(pos).equals(state) || protectedBlock(state, pos))
                return false;
            if (!level.destroyBlock(pos, !released, owner)) return false;
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, owner, pos, state, null);
            return true;
        }
    }
    // Each next() accounts for exactly one scan or commit visit, including rejected positions.
    private static Iterator<@Nullable BlockPos> mapPositions(Iterator<BlockPos> scan, Predicate<BlockPos> accepts) {
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

    public static @Nullable Work reserve(ServerPlayer owner) {
        QUEUE.removeIf(Work::finished);
        if (QUEUE.size() >= MAX_WORK) return null;
        var work = new Work(owner);
        QUEUE.addLast(work);
        return work;
    }

    public static void tick() {
        if (QUEUE.isEmpty()) return;
        var event = new TerrainEvent();
        event.begin();
        var budget = new Budget();
        int idle = 0;
        while (!QUEUE.isEmpty() && budget.available() && idle < QUEUE.size()) {
            var work = QUEUE.removeFirst();
            if (!work.finished()) {
                idle = work.ready() ? idle + 1 : 0;
                // Keep the active reservation visible to callbacks that reserve or cancel work.
                QUEUE.addLast(work);
                budget.visit(work);
            }
        }
        event.end();
        if (event.shouldCommit()) {
            event.visits = budget.candidates;
            event.blocks = budget.writes;
            event.pending = QUEUE.size();
            event.commit();
        }
    }

    private static final class Budget {
        private final long deadline = System.nanoTime() + NANOS_PER_TICK;
        private int candidates;
        private int writes;

        private boolean available() {
            return candidates < CANDIDATES_PER_TICK && writes < WRITES_PER_TICK && System.nanoTime() < deadline;
        }

        private void visit(Work work) {
            for (int i = 0; i < SLICE; i++) {
                if (work.finished() || !available()) return;
                candidates++;
                if (work.advance()) writes++;
                if (work.ready()) return;
            }
        }
    }

    public static void clear() {
        QUEUE.forEach(Work::close);
        QUEUE.clear();
    }

    private record Candidate(BlockPos position, double distance) {}
}
