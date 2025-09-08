package dev.lsdmc.Shakedown.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Responsible for persisting and retrieving runtime data such as last
 * shakedown timestamps. Data is stored in a YAML file (`data.yml`)
 * inside the plugin’s data folder. The format is simple: a map of
 * identifiers (player UUIDs or region IDs) to epoch second timestamps.
 */
public final class DataManager {
    private final Plugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    // In memory cache for quick lookups
    private final Map<String, Long> lastShakedowns;

    public DataManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.lastShakedowns = new HashMap<>();
        loadData();
    }

    /**
     * Returns the timestamp (epoch seconds) of the last shakedown for a given
     * identifier. The identifier can either be a player UUID string or
     * region ID. If no entry exists, -1 is returned.
     */
    public long getLastShakedown(String identifier) {
        return lastShakedowns.getOrDefault(identifier, -1L);
    }

    /**
     * Updates the last shakedown time for the given identifier to the
     * current instant.
     */
    public void recordShakedown(String identifier) {
        long now = Instant.now().getEpochSecond();
        lastShakedowns.put(identifier, now);
        dataConfig.set("lastShakedowns." + identifier, now);
        saveData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                // Create parent directories and file if it doesn’t exist
                dataFile.getParentFile().mkdirs();
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create data.yml file");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        // Populate the in memory map
        if (dataConfig.contains("lastShakedowns")) {
            for (String key : dataConfig.getConfigurationSection("lastShakedowns").getKeys(false)) {
                long timestamp = dataConfig.getLong("lastShakedowns." + key);
                lastShakedowns.put(key, timestamp);
            }
        }
    }

    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }
}


