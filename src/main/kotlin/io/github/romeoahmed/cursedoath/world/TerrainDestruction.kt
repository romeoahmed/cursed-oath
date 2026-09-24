package io.github.romeoahmed.cursedoath.world

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.IdentityHashMap
import java.util.PriorityQueue

/** Bounded, round-robin excavation. Reservations include casts still preparing. */
internal object TerrainDestruction {
    private const val MAX_WORK = 16
    private const val CANDIDATES_PER_TICK = 4096
    private const val WRITES_PER_TICK = 256
    private const val SLICE = 16
    private const val NANOS_PER_TICK = 4_000_000L
    private const val LIFETIME = 600L
    private val worlds = IdentityHashMap<ServerLevel, ArrayDeque<Work>>()

    class Work(
        val owner: ServerPlayer,
    ) {
        private val level = owner.level()
        private val expires = level.gameTime + LIFETIME

        // Null entries account for geometry visits that do not commit a block removal.
        private var positions: Iterator<BlockPos?>? = null
        var finished = false
            private set
        var blocked = false
            private set
        var persistent = false
        var drops = true
        var onComplete: (() -> Unit)? = null
        private var source: ((Vec3) -> Vec3)? = null
        val ready: Boolean get() = positions == null

        fun sweep(
            volume: SweptVolume,
            origin: Vec3 = volume.start,
        ) {
            cuts(listOf(volume), origin)
        }

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
                        if (volumes.any { it.entry(AABB(pos)) != null }) {
                            val distance = Vec3.atCenterOf(pos).subtract(origin).dot(first.forward)
                            candidates.add(Candidate(pos.immutable(), distance))
                        }
                        yield(null)
                    }
                    while (candidates.isNotEmpty()) yield(candidates.remove().position)
                }.iterator()
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
            onComplete = null
        }

        private fun stop(): Boolean {
            blocked = true
            close()
            return false
        }

        private val validOwner: Boolean get() =
            owner.level() === level && owner.isAlive && !owner.isRemoved && !owner.isSpectator

        internal fun advance(): Boolean {
            if (!validOwner || level.gameTime >= expires) {
                return stop()
            }
            val cursor = positions ?: return false
            if (!cursor.hasNext()) {
                positions = null
                val complete = onComplete
                onComplete = null
                if (!persistent) close()
                complete?.invoke()
                return false
            }
            return cursor.next()?.let(::excavate) ?: false
        }

        private fun excavate(pos: BlockPos): Boolean {
            if (level.chunkSource.getChunkNow(
                    SectionPos.blockToSectionCoord(pos.x),
                    SectionPos.blockToSectionCoord(pos.z),
                ) == null
            ) {
                return stop()
            }
            if (!level.worldBorder.isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false
            val state = level.getBlockState(pos)
            if (state.isAir) return false
            if (protected(state, pos)) {
                blocked = true
                return false
            }
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
                blocked = true
                return false
            }
            // A callback may replace the block. Never destroy a state that was not checked.
            if (!validOwner || level.getBlockState(pos) != state || protected(state, pos)) return false
            if (!level.destroyBlock(pos, drops, owner)) return false
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, owner, pos, state, null)
            return true
        }
    }

    fun reserve(owner: ServerPlayer): Work? {
        val queue = worlds.getOrPut(owner.level()) { ArrayDeque() }
        queue.removeAll { it.finished }
        if (queue.size >= MAX_WORK) return null
        return Work(owner).also(queue::addLast)
    }

    fun tick(level: ServerLevel) {
        val queue = worlds[level] ?: return
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
        if (queue.isEmpty()) worlds.remove(level)
    }

    private class Budget {
        private val deadline = System.nanoTime() + NANOS_PER_TICK
        private var candidates = 0
        private var writes = 0
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
        worlds.values.forEach { queue -> queue.forEach(Work::close) }
        worlds.clear()
    }

    private data class Candidate(
        val position: BlockPos,
        val distance: Double,
    )
}
