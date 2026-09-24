package io.github.romeoahmed.cursedoath.world

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue

/** Bounded, round-robin excavation. Reservations include casts still preparing. */
internal object TerrainDestruction {
    private const val MAX_WORK = 16
    private const val CANDIDATES_PER_TICK = 16384
    internal const val WRITES_PER_TICK = 1024
    private const val SLICE = 16
    private const val NANOS_PER_TICK = 4_000_000L
    private const val LIFETIME = 600L
    private const val MAX_SEGMENTS = 64
    private val queue = ArrayDeque<Work>()

    class Work(
        val owner: ServerPlayer,
    ) {
        private val level = owner.level()
        private val expires = level.gameTime + LIFETIME

        // Null entries account for geometry visits that do not commit a block removal.
        private var positions: Iterator<BlockPos?>? = null
        private val pending = ArrayDeque<Iterator<BlockPos?>>()
        private var released = false
        var finished = false
            private set
        var persistent = false
        var onComplete: (() -> Unit)? = null
        private var source: ((Vec3) -> Vec3)? = null
        val ready: Boolean get() = positions == null && pending.isEmpty()

        fun cuts(
            volumes: List<SweptVolume>,
            origin: Vec3 = volumes.first().start,
        ) {
            check(ready && !finished)
            val first = volumes.first()
            val bounds = volumes.map { it.bounds }.reduce(AABB::minmax)
            source = { first.source(it, origin) }
            positions =
                sequence {
                    val candidates = PriorityQueue(compareBy<Candidate> { it.distance }.thenBy { it.position.asLong() })
                    for (pos in BlockPos.betweenClosed(bounds)) {
                        val box = AABB(pos)
                        if (volumes.any { it.entry(box) != null }) {
                            val distance = Vec3.atCenterOf(pos).subtract(origin).dot(first.forward)
                            candidates.add(Candidate(pos.immutable(), distance))
                        }
                        yield(null)
                    }
                    while (candidates.isNotEmpty()) yield(candidates.remove().position)
                }.iterator()
        }

        /** One bounded segment per flight tick; no sorting or cover rays for a piercing attack. */
        fun pierce(
            volume: SweptVolume,
            previous: SweptVolume?,
        ) {
            check(!finished && pending.size < MAX_SEGMENTS)
            released = true
            persistent = true
            pending.addLast(
                BlockPos
                    .betweenClosed(volume.bounds)
                    .asSequence()
                    .map { pos ->
                        val box = AABB(pos)
                        if (volume.entry(box) != null && previous?.entry(box) == null) pos.immutable() else null
                    }.iterator(),
            )
        }

        fun seal() {
            persistent = false
            if (ready) close()
        }

        fun sphere(
            center: Vec3,
            radius: Double,
        ) {
            check(ready && !finished)
            source = null
            val extent = kotlin.math.ceil(radius).toInt()
            positions =
                BlockPos
                    .withinBoxByManhattanDistance(BlockPos.containing(center), extent, extent, extent)
                    .asSequence()
                    .map { if (AABB(it).distanceToSqr(center) < radius * radius) it.immutable() else null }
                    .iterator()
        }

        fun close() {
            finished = true
            positions = null
            pending.clear()
            onComplete = null
            source = null
        }

        private fun stop(): Boolean {
            close()
            return false
        }

        private val validOwner: Boolean get() =
            released || (owner.level() === level && owner.isAlive && !owner.isRemoved && !owner.isSpectator)

        internal fun advance(): Boolean {
            if (!validOwner || (!released && level.gameTime >= expires)) {
                return stop()
            }
            val cursor = positions ?: pending.removeFirstOrNull()?.also { positions = it } ?: return false
            return if (!cursor.hasNext()) {
                positions = null
                val complete = onComplete
                onComplete = null
                if (!persistent && pending.isEmpty()) close()
                complete?.invoke()
                false
            } else {
                cursor.next()?.let(::excavate) ?: false
            }
        }

        private fun excavate(pos: BlockPos): Boolean {
            if (level.chunkSource.getChunkNow(
                    SectionPos.blockToSectionCoord(pos.x),
                    SectionPos.blockToSectionCoord(pos.z),
                ) == null
            ) {
                return if (released) false else stop()
            }
            if (!level.worldBorder.isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false
            val state = level.getBlockState(pos)
            if (state.isAir) return false
            if (protected(state, pos)) return false
            val origin = source?.invoke(Vec3.atCenterOf(pos))
            if (origin != null && !exposed(origin, pos)) return false
            return breakBlock(pos, state)
        }

        private fun exposed(
            origin: Vec3,
            pos: BlockPos,
        ): Boolean {
            if (!level.hasLoadedChunks(AABB(origin, Vec3.atCenterOf(pos)))) return stop()
            val hit =
                level.clipIncludingBorder(
                    ClipContext(origin, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, owner),
                )
            return hit.type == HitResult.Type.MISS || hit.blockPos == pos
        }

        private fun protected(
            state: BlockState,
            pos: BlockPos,
        ): Boolean {
            val material = state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || !state.fluidState.isEmpty
            val restricted =
                !level.mayInteract(owner, pos) ||
                    owner.blockActionRestricted(level, pos, owner.gameMode.gameModeForPlayer)
            return material || restricted
        }

        private fun breakBlock(
            pos: BlockPos,
            state: BlockState,
        ): Boolean {
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, owner, pos, state, null)) {
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, owner, pos, state, null)
                return false
            }
            // A callback may cancel the work or replace the block before this removal commits.
            if (finished || !validOwner) return false
            if (level.getBlockState(pos) != state || protected(state, pos)) return false
            if (!level.destroyBlock(pos, !released, owner)) return false
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, owner, pos, state, null)
            return true
        }
    }

    fun reserve(owner: ServerPlayer): Work? {
        queue.removeAll { it.finished }
        if (queue.size >= MAX_WORK) return null
        return Work(owner).also(queue::addLast)
    }

    fun tick() {
        if (queue.isEmpty()) return
        val event = TerrainEvent()
        event.begin()
        val budget = Budget()
        var idle = 0
        while (queue.isNotEmpty() && budget.available && idle < queue.size) {
            val work = queue.removeFirst()
            if (!work.finished) {
                if (work.ready) idle++ else idle = 0
                budget.visit(work)
                if (!work.finished) queue.addLast(work)
            }
        }
        event.end()
        if (event.shouldCommit()) {
            event.visits = budget.candidates
            event.blocks = budget.writes
            event.pending = queue.size
            event.commit()
        }
    }

    private class Budget {
        private val deadline = System.nanoTime() + NANOS_PER_TICK
        var candidates = 0
            private set
        var writes = 0
            private set
        val available: Boolean get() =
            candidates < CANDIDATES_PER_TICK && writes < WRITES_PER_TICK &&
                System.nanoTime() < deadline

        fun visit(work: Work) {
            repeat(SLICE) {
                if (work.finished || !available) return
                candidates++
                if (work.advance()) writes++
                if (work.ready) return
            }
        }
    }

    fun clear() {
        queue.forEach(Work::close)
        queue.clear()
    }

    private data class Candidate(
        val position: BlockPos,
        val distance: Double,
    )
}
