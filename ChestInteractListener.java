package com.randomlootpvp.manager;

import com.randomlootpvp.RandomLootPvP;
import com.randomlootpvp.loot.LootGenerator;
import com.randomlootpvp.model.TrackedChest;
import com.randomlootpvp.util.ChunkSafeUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of all random PvP loot chests.
 *
 * <p><strong>Thread-safety design:</strong>
 * <ul>
 *   <li>Candidate X/Z coordinates are chosen on an async thread to spread CPU cost.</li>
 *   <li>All Bukkit API calls (chunk checks, block placement, inventory fill) happen on
 *       the main server thread via {@code runTask}.</li>
 *   <li>We NEVER force chunk generation.  {@code world.isChunkGenerated()} is checked
 *       before any block access.</li>
 * </ul>
 * </p>
 */
public class ChestManager {

    private final RandomLootPvP plugin;
    private final Logger log;
    private final Random rng = new Random();

    /**
     * Active chest registry. Key = serialised "world:x:y:z" for fast lookup.
     * Values hold spawn time for despawn tracking.
     */
    private final Map<String, TrackedChest> activeChests = new ConcurrentHashMap<>();

    private BukkitTask spawnTask;
    private BukkitTask cleanupTask;

    public ChestManager(RandomLootPvP plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        long intervalTicks = (long) plugin.getConfigManager().getSpawnIntervalSeconds() * 20L;

        // Spawn/top-up task: runs every spawn-interval-seconds
        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickSpawn();
            }
        }.runTaskTimer(plugin, 20L * 5, intervalTicks); // 5-second startup delay

        // Cleanup/despawn task: runs every 30 seconds
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCleanup();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30);

        log.info("ChestManager started. Spawn every " +
                plugin.getConfigManager().getSpawnIntervalSeconds() + "s, max " +
                plugin.getConfigManager().getMaxChests() + " chests.");
    }

    public void shutdown() {
        if (spawnTask != null && !spawnTask.isCancelled()) spawnTask.cancel();
        if (cleanupTask != null && !cleanupTask.isCancelled()) cleanupTask.cancel();
        activeChests.clear();
    }

    // ── Spawn tick ────────────────────────────────────────────────────────────

    /**
     * Called on the main thread each spawn interval.
     * If we are below the max-chests target we attempt to add more.
     */
    private void tickSpawn() {
        int max = plugin.getConfigManager().getMaxChests();
        int current = activeChests.size();

        if (current >= max) return;

        int needed = max - current;

        // Build candidate pool asynchronously, then place on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            World world = Bukkit.getWorld(plugin.getConfigManager().getWorldName());
            if (world == null) return;

            List<Player> online = new ArrayList<>(world.getPlayers());

            // Generate candidate coordinates on async thread (CPU-safe)
            List<int[]> candidates = buildCandidates(world, online, needed);

            if (candidates.isEmpty()) {
                log.fine("No valid candidate locations found this cycle.");
                return;
            }

            // Hand back to main thread for all Bukkit API calls
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int[] coord : candidates) {
                    if (activeChests.size() >= max) break;
                    tryPlaceChest(world, coord[0], coord[1]);
                }
            });
        });
    }

    /**
     * Builds a list of candidate [x, z] pairs.
     * Pure computation — no Bukkit API, safe on async thread.
     *
     * @param online players currently in the world (used for proximity spawning)
     * @param needed how many candidates to try to produce
     */
    private List<int[]> buildCandidates(World world, List<Player> online, int needed) {
        List<int[]> result = new ArrayList<>();

        int maxAttempts = plugin.getConfigManager().getMaxAttemptsPerCycle();
        int nearRadius = plugin.getConfigManager().getSpawnNearPlayerRadius();
        int globalRadius = plugin.getConfigManager().getSpawnRadius();
        int minDist = plugin.getConfigManager().getMinimumDistanceFromPlayer();

        for (int attempt = 0; attempt < maxAttempts && result.size() < needed * 3; attempt++) {
            int x, z;

            if (!online.isEmpty()) {
                // Pick a random online player as the anchor
                Player anchor = online.get(rng.nextInt(online.size()));
                int ax = anchor.getLocation().getBlockX();
                int az = anchor.getLocation().getBlockZ();

                x = ax + rng.nextInt(nearRadius * 2) - nearRadius;
                z = az + rng.nextInt(nearRadius * 2) - nearRadius;

                // Ensure we are not too close to ANY player
                boolean tooClose = false;
                for (Player p : online) {
                    int dx = p.getLocation().getBlockX() - x;
                    int dz = p.getLocation().getBlockZ() - z;
                    if (Math.sqrt(dx * dx + dz * dz) < minDist) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;

            } else {
                // No players — use global radius but avoid stupidly huge coords
                int safeRadius = Math.min(globalRadius, 2000);
                x = rng.nextInt(safeRadius * 2) - safeRadius;
                z = rng.nextInt(safeRadius * 2) - safeRadius;
            }

            result.add(new int[]{x, z});
        }

        return result;
    }

    /**
     * Attempts to place a chest at (x, z).
     * MUST be called from the main server thread.
     *
     * <p>This method:
     * <ol>
     *   <li>Checks chunk is already generated — if not, skips.</li>
     *   <li>Loads chunk WITHOUT generating (generate=false).</li>
     *   <li>Calls getHighestBlockYAt only after confirming the chunk is safe.</li>
     *   <li>Validates the surface and places the chest.</li>
     * </ol>
     * </p>
     */
    private void tryPlaceChest(World world, int x, int z) {
        // ── Step 1: chunk generation check — NO GENERATION ───────────────────
        if (!ChunkSafeUtil.isChunkSafe(world, x, z)) {
            return; // chunk not generated, skip silently
        }

        // ── Step 2: find a surface Y — chunk is safe now ──────────────────────
        int y = ChunkSafeUtil.getHighestSolidY(world, x, z);
        if (y < 0) return;

        // ── Step 3: validate surface ──────────────────────────────────────────
        if (!ChunkSafeUtil.isValidChestSurface(world, x, y, z)) return;

        // ── Step 4: check for nearby duplicate chests ─────────────────────────
        String key = locationKey(world, x, y, z);
        if (activeChests.containsKey(key)) return;

        // ── Step 5: place the chest ───────────────────────────────────────────
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false); // false = skip physics update

        if (!(block.getState() instanceof Chest chest)) {
            block.setType(Material.AIR, false);
            return;
        }

        LootGenerator.fillChest(
                chest.getInventory(),
                plugin.getConfigManager().getLegendaryChance()
        );

        // ── Step 6: register and announce ────────────────────────────────────
        Location loc = new Location(world, x, y, z);
        TrackedChest tracked = new TrackedChest(loc);
        activeChests.put(key, tracked);

        // Visual + audio feedback — visible to nearby players
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1, 0.5), 30, 0.3, 0.5, 0.3, 0.02);
        world.playSound(loc, Sound.BLOCK_ENDER_CHEST_OPEN, 1.5f, 0.8f);

        log.fine("Placed loot chest at " + x + "," + y + "," + z);
    }

    // ── Cleanup tick ──────────────────────────────────────────────────────────

    /**
     * Removes expired chests. Called on the main thread.
     */
    private void tickCleanup() {
        int despawnMinutes = plugin.getConfigManager().getDespawnAfterMinutes();
        Iterator<Map.Entry<String, TrackedChest>> it = activeChests.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, TrackedChest> entry = it.next();
            TrackedChest tc = entry.getValue();

            if (tc.isExpired(despawnMinutes)) {
                Location loc = tc.getLocation();
                Block block = loc.getBlock();

                // Only remove if it is still a chest (player might have broken it already)
                if (block.getType() == Material.CHEST) {
                    block.setType(Material.AIR, false);
                    // Small particle burst to signal removal
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 10);
                }

                it.remove();
                log.fine("Despawned expired chest at " + loc);
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Manually trigger a single chest spawn attempt near a random online player
     * (or at a random location if no one is online).
     */
    public void spawnRandomChest() {
        World world = Bukkit.getWorld(plugin.getConfigManager().getWorldName());
        if (world == null) {
            log.warning("World '" + plugin.getConfigManager().getWorldName() + "' not found.");
            return;
        }

        List<Player> online = new ArrayList<>(world.getPlayers());
        List<int[]> candidates = buildCandidates(world, online, 5);

        for (int[] coord : candidates) {
            int before = activeChests.size();
            tryPlaceChest(world, coord[0], coord[1]);
            if (activeChests.size() > before) return; // placed one
        }

        log.warning("spawnRandomChest: could not find a valid generated chunk location.");
    }

    /** Removes the chest at the exact location (called when a player breaks it). */
    public void removeChest(Location loc) {
        String key = locationKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        activeChests.remove(key);
    }

    /** Marks a chest as opened (called from interact listener). */
    public void markOpened(Location loc) {
        String key = locationKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        TrackedChest tc = activeChests.get(key);
        if (tc != null) tc.setOpened(true);
    }

    /** Returns true if the given location is a tracked loot chest. */
    public boolean isTrackedChest(Location loc) {
        String key = locationKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return activeChests.containsKey(key);
    }

    /** Removes and clears all active loot chests. */
    public void removeAll() {
        for (TrackedChest tc : activeChests.values()) {
            Location loc = tc.getLocation();
            if (loc.getBlock().getType() == Material.CHEST) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }
        activeChests.clear();
        log.info("Removed all loot chests.");
    }

    /** Refills all active chests with fresh loot. */
    public void refillAll() {
        int legendaryChance = plugin.getConfigManager().getLegendaryChance();
        for (TrackedChest tc : activeChests.values()) {
            Block block = tc.getLocation().getBlock();
            if (block.getState() instanceof Chest chest) {
                chest.getInventory().clear();
                LootGenerator.fillChest(chest.getInventory(), legendaryChance);
            }
        }
        log.info("Refilled " + activeChests.size() + " loot chests.");
    }

    /** Returns the current count of active tracked chests. */
    public int getChestCount() {
        return activeChests.size();
    }

    /** Returns an unmodifiable view of all tracked chests (for admin info). */
    public Collection<TrackedChest> getTrackedChests() {
        return Collections.unmodifiableCollection(activeChests.values());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String locationKey(World world, int x, int y, int z) {
        return world.getName() + ":" + x + ":" + y + ":" + z;
    }
}
