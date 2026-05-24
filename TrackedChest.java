package com.randomlootpvp.util;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Utility methods for chunk-safe block inspection.
 *
 * <p>Critical rule: we NEVER allow chunk generation.
 * All methods here are designed to be called only AFTER confirming
 * the chunk is already generated. Call from the main thread only.</p>
 */
public final class ChunkSafeUtil {

    private ChunkSafeUtil() {}

    /**
     * Returns true if the chunk at the given block coordinates
     * is already generated AND safe to access without forcing generation.
     *
     * Must be called on the main server thread.
     */
    public static boolean isChunkSafe(World world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        // isChunkGenerated does NOT force generation — safe to call
        if (!world.isChunkGenerated(chunkX, chunkZ)) {
            return false;
        }

        // Load the chunk WITHOUT forcing generation (false = no generate)
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            boolean loaded = world.loadChunk(chunkX, chunkZ, false);
            if (!loaded) {
                return false;
            }
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        return chunk.isLoaded();
    }

    /**
     * Gets the highest solid, non-liquid block Y at the given X/Z,
     * assuming the chunk is already verified to be safe.
     *
     * Returns -1 if no valid surface is found.
     */
    public static int getHighestSolidY(World world, int x, int z) {
        // Use MOTION_BLOCKING_NO_LEAVES so we land on terrain not tree tops
        // MOTION_BLOCKING_NO_LEAVES: lands on ground/terrain, not treetops
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

        // Walk down until we find a real solid block (not air/liquid)
        for (int ty = y; ty > world.getMinHeight(); ty--) {
            Block block = world.getBlockAt(x, ty, z);
            Material mat = block.getType();

            if (mat == Material.AIR || mat == Material.CAVE_AIR
                    || mat == Material.VOID_AIR
                    || mat == Material.WATER || mat == Material.LAVA
                    || !block.getType().isSolid()) {
                continue;
            }

            // Found a solid block — return the Y above it for chest placement
            return ty + 1;
        }

        return -1;
    }

    /**
     * Returns true if the location is valid for placing a chest:
     * - The target block is air
     * - The block below is solid and not liquid
     * - Not inside a liquid column
     */
    public static boolean isValidChestSurface(World world, int x, int y, int z) {
        if (y < world.getMinHeight() + 1 || y >= world.getMaxHeight()) return false;

        Block target = world.getBlockAt(x, y, z);
        Block below = world.getBlockAt(x, y - 1, z);

        if (target.getType() != Material.AIR) return false;

        Material belowMat = below.getType();
        if (belowMat == Material.WATER || belowMat == Material.LAVA) return false;
        if (!belowMat.isSolid()) return false;

        // Make sure there's at least 1 block of headroom
        Block above = world.getBlockAt(x, y + 1, z);
        if (above.getType() != Material.AIR && above.getType() != Material.CAVE_AIR) return false;

        return true;
    }
}
