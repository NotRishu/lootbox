package com.randomlootpvp.manager;

import com.randomlootpvp.RandomLootPvP;

public class ConfigManager {

    private final RandomLootPvP plugin;

    public ConfigManager(RandomLootPvP plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    /** Maximum number of active loot chests in the world at once. */
    public int getMaxChests() {
        return plugin.getConfig().getInt("max-chests", 15);
    }

    /**
     * Global spawn radius in blocks from 0,0.
     * ONLY used when no players are online.
     */
    public int getSpawnRadius() {
        return plugin.getConfig().getInt("spawn-radius", 1000);
    }

    /**
     * Radius around each online player within which chests may spawn.
     * Keeps chests near players and avoids loading far-away chunks.
     */
    public int getSpawnNearPlayerRadius() {
        return plugin.getConfig().getInt("spawn-near-player-radius", 300);
    }

    /** How often (in seconds) the system tries to top up the chest count. */
    public int getSpawnIntervalSeconds() {
        return plugin.getConfig().getInt("spawn-interval-seconds", 60);
    }

    /** Minutes after which a chest is automatically removed if untouched. */
    public int getDespawnAfterMinutes() {
        return plugin.getConfig().getInt("despawn-after-minutes", 20);
    }

    /** A chest will NOT be placed within this many blocks of any player. */
    public int getMinimumDistanceFromPlayer() {
        return plugin.getConfig().getInt("minimum-distance-from-player", 30);
    }

    /** Name of the world where chests are spawned. */
    public String getWorldName() {
        return plugin.getConfig().getString("world", "world");
    }

    /** Maximum number of placement attempts per spawn cycle. */
    public int getMaxAttemptsPerCycle() {
        return plugin.getConfig().getInt("max-attempts-per-cycle", 30);
    }

    /** Legendary item drop chance (0–100). */
    public int getLegendaryChance() {
        return plugin.getConfig().getInt("legendary-chance", 5);
    }
}
