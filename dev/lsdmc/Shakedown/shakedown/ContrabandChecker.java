package dev.lsdmc.Shakedown.shakedown;

import dev.lsdmc.Shakedown.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class that encapsulates the logic for determining if a given
 * {@link ItemStack} is considered contraband. Contraband definitions are
 * configured in {@code config.yml} through lists of material names and
 * custom model data values.
 */
public final class ContrabandChecker {
    private final Set<Material> contrabandMaterials;
    private final Set<Integer> contrabandCustomData;

    public ContrabandChecker(ConfigManager configManager) {
        // Populate material whitelist; ignore invalid material names
        contrabandMaterials = configManager.getContrabandMaterials().stream()
                .map(String::toUpperCase)
                .map(name -> {
                    try {
                        return Material.valueOf(name);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(m -> m != null)
                .collect(Collectors.toSet());

        // Parse customModelData entries; ignore non‑numeric values
        contrabandCustomData = configManager.getContrabandCustomData().stream()
                .map(entry -> {
                    try {
                        return Integer.parseInt(entry);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(i -> i != null)
                .collect(Collectors.toSet());
    }

    /**
     * Determines whether the given item should be treated as contraband.
     *
     * @param item the item to evaluate
     * @return true if the item is contraband, false otherwise
     */
    public boolean isContraband(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        // Material match
        if (contrabandMaterials.contains(item.getType())) {
            return true;
        }
        // Custom model data match
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            return contrabandCustomData.contains(cmd);
        }
        return false;
    }
}


