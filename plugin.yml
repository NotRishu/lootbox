package com.randomlootpvp.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Generates randomised PvP loot for chest inventories.
 *
 * <p>Three tiers:</p>
 * <ul>
 *   <li>Common  – food, basic tools, arrows</li>
 *   <li>Rare    – iron/diamond gear, golden apples, pearls</li>
 *   <li>Legendary – netherite gear, enchanted items, special pieces</li>
 * </ul>
 */
public final class LootGenerator {

    private LootGenerator() {}

    private static final Random RNG = new Random();

    // ── Common loot ───────────────────────────────────────────────────────────
    private static final Material[] COMMON = {
            Material.COOKED_BEEF,
            Material.COOKED_PORKCHOP,
            Material.BREAD,
            Material.ARROW,
            Material.ARROW,
            Material.ARROW,
            Material.WATER_BUCKET,
            Material.SNOWBALL,
            Material.GRAVEL,
            Material.IRON_NUGGET,
            Material.COAL,
    };

    // ── Rare loot ─────────────────────────────────────────────────────────────
    private static final Material[] RARE = {
            Material.IRON_HELMET,
            Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS,
            Material.IRON_BOOTS,
            Material.IRON_SWORD,
            Material.IRON_AXE,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_SWORD,
            Material.BOW,
            Material.CROSSBOW,
            Material.SHIELD,
            Material.ENDER_PEARL,
            Material.GOLDEN_APPLE,
            Material.LAVA_BUCKET,
            Material.FLINT_AND_STEEL,
    };

    // ── Legendary loot ────────────────────────────────────────────────────────
    private static final Material[] LEGENDARY = {
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_AXE,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.TOTEM_OF_UNDYING,
    };

    /**
     * Fills the given inventory with randomised PvP loot.
     *
     * @param inventory      the chest inventory to fill
     * @param legendaryChance 0–100 probability of a legendary item appearing
     */
    public static void fillChest(Inventory inventory, int legendaryChance) {
        inventory.clear();

        // 4–8 items per chest
        int itemCount = 4 + RNG.nextInt(5);
        Set<Integer> usedSlots = new HashSet<>();

        for (int i = 0; i < itemCount; i++) {
            int slot = pickFreeSlot(inventory.getSize(), usedSlots);
            if (slot < 0) break;

            ItemStack item = pickItem(legendaryChance);
            inventory.setItem(slot, item);
            usedSlots.add(slot);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack pickItem(int legendaryChance) {
        int roll = RNG.nextInt(100);

        Material mat;
        boolean enchant = false;
        int enchantLevel = 1;

        if (roll < legendaryChance) {
            // Legendary
            mat = LEGENDARY[RNG.nextInt(LEGENDARY.length)];
            enchant = true;
            enchantLevel = 1 + RNG.nextInt(3); // I–III
        } else if (roll < legendaryChance + 35) {
            // Rare
            mat = RARE[RNG.nextInt(RARE.length)];
            enchant = RNG.nextInt(100) < 40;
            enchantLevel = 1 + RNG.nextInt(2); // I–II
        } else {
            // Common
            mat = COMMON[RNG.nextInt(COMMON.length)];
            enchant = false;
        }

        ItemStack item;

        // Stack some stackable commons
        if (mat == Material.ARROW) {
            item = new ItemStack(mat, 8 + RNG.nextInt(17)); // 8–24
        } else if (mat == Material.COAL || mat == Material.GRAVEL || mat == Material.SNOWBALL) {
            item = new ItemStack(mat, 4 + RNG.nextInt(5));
        } else {
            item = new ItemStack(mat, 1);
        }

        if (enchant) {
            applyRandomEnchantment(item, enchantLevel);
        }

        return item;
    }

    private static void applyRandomEnchantment(ItemStack item, int level) {
        Material mat = item.getType();

        List<Enchantment> candidates = new ArrayList<>();

        if (isWeapon(mat)) {
            candidates.add(Enchantment.SHARPNESS);
            candidates.add(Enchantment.FIRE_ASPECT);
            candidates.add(Enchantment.KNOCKBACK);
            candidates.add(Enchantment.LOOTING);
        }
        if (isArmour(mat)) {
            candidates.add(Enchantment.PROTECTION);
            candidates.add(Enchantment.FIRE_PROTECTION);
            candidates.add(Enchantment.BLAST_PROTECTION);
            candidates.add(Enchantment.PROJECTILE_PROTECTION);
            candidates.add(Enchantment.FEATHER_FALLING);
        }
        if (mat == Material.BOW || mat == Material.CROSSBOW) {
            candidates.add(Enchantment.POWER);
            candidates.add(Enchantment.FLAME);
            candidates.add(Enchantment.PUNCH);
            candidates.add(Enchantment.INFINITY);
        }
        if (mat == Material.SHIELD) {
            candidates.add(Enchantment.UNBREAKING);
        }

        if (candidates.isEmpty()) return;

        Enchantment chosen = candidates.get(RNG.nextInt(candidates.size()));

        // Cap level to enchantment max
        int safeLevel = Math.min(level, chosen.getMaxLevel());
        item.addUnsafeEnchantment(chosen, safeLevel);
    }

    private static boolean isWeapon(Material mat) {
        return mat == Material.IRON_SWORD || mat == Material.DIAMOND_SWORD
                || mat == Material.NETHERITE_SWORD || mat == Material.IRON_AXE
                || mat == Material.DIAMOND_AXE || mat == Material.NETHERITE_AXE;
    }

    private static boolean isArmour(Material mat) {
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private static int pickFreeSlot(int size, Set<Integer> used) {
        if (used.size() >= size) return -1;
        int slot;
        int attempts = 0;
        do {
            slot = RNG.nextInt(size);
            attempts++;
            if (attempts > size * 3) return -1;
        } while (used.contains(slot));
        return slot;
    }
}
