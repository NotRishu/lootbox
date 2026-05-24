package com.randomlootpvp;

import com.randomlootpvp.commands.RandomLootCommand;
import com.randomlootpvp.listeners.ChestBreakListener;
import com.randomlootpvp.listeners.ChestInteractListener;
import com.randomlootpvp.manager.ChestManager;
import com.randomlootpvp.manager.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RandomLootPvP extends JavaPlugin {

    private ChestManager chestManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Load config wrapper
        this.configManager = new ConfigManager(this);

        // Initialize chest manager
        this.chestManager = new ChestManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChestBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestInteractListener(this), this);

        // Register commands
        RandomLootCommand cmd = new RandomLootCommand(this);
        getCommand("randomloot").setExecutor(cmd);
        getCommand("randomloot").setTabCompleter(cmd);

        // Start scheduled tasks
        chestManager.start();

        getLogger().info("RandomLootPvP v2.0 enabled — chunk-safe mode active.");
        getLogger().info("Spawn interval: " + configManager.getSpawnIntervalSeconds() + "s | Max chests: " + configManager.getMaxChests());
    }

    @Override
    public void onDisable() {
        if (chestManager != null) {
            chestManager.shutdown();
        }
        getLogger().info("RandomLootPvP disabled. Chests cleaned up.");
    }

    public ChestManager getChestManager() {
        return chestManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
