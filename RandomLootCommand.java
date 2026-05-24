package com.randomlootpvp.listeners;

import com.randomlootpvp.RandomLootPvP;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ChestBreakListener implements Listener {

    private final RandomLootPvP plugin;

    public ChestBreakListener(RandomLootPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CHEST) return;

        if (plugin.getChestManager().isTrackedChest(event.getBlock().getLocation())) {
            plugin.getChestManager().removeChest(event.getBlock().getLocation());

            Player player = event.getPlayer();
            player.sendMessage("§6[RandomLoot] §eYou broke a loot chest!");
        }
    }
}
