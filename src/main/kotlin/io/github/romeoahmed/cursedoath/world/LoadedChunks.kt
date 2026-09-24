package io.github.romeoahmed.cursedoath.world

import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB

/** Checks ready chunks without loading or waiting; chunk tickets alone do not establish readiness. */
internal fun ServerLevel.hasLoadedChunks(bounds: AABB): Boolean {
    for (x in SectionPos.blockToSectionCoord(bounds.minX)..SectionPos.blockToSectionCoord(bounds.maxX)) {
        for (z in SectionPos.blockToSectionCoord(bounds.minZ)..SectionPos.blockToSectionCoord(bounds.maxZ)) {
            if (chunkSource.getChunkNow(x, z) == null) return false
        }
    }
    return true
}
