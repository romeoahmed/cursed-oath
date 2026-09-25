package io.github.romeoahmed.cursedoath.world;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/// Checks ready chunks without loading or waiting; ticket eligibility does not imply readiness.
public final class LoadedChunks {
    private LoadedChunks() {}

    public static boolean contains(ServerLevel level, AABB bounds) {
        for (int x = SectionPos.blockToSectionCoord(bounds.minX);
                x <= SectionPos.blockToSectionCoord(bounds.maxX);
                x++) {
            for (int z = SectionPos.blockToSectionCoord(bounds.minZ);
                    z <= SectionPos.blockToSectionCoord(bounds.maxZ);
                    z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }
}
