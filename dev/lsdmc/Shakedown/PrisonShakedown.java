package dev.lsdmc.Shakedown;

import dev.lsdmc.Shakedown.config.ConfigManager;
import dev.lsdmc.Shakedown.data.DataManager;
import dev.lsdmc.Shakedown.shakedown.ShakedownManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the Shakedown plugin. This class is responsible for wiring
 * together all of the sub‑systems and exposing the plugin’s API to
 * Paper/Spigot. The plugin requires WorldGuard and AdvancedRegionMarket to be
 * installed and will register commands and listeners on enable.
 */
public final class PrisonShakedown extends JavaPlugin {
    private static PrisonShakedown instance;
    private ConfigManager configManager;
    private DataManager dataManager;
    private ShakedownManager shakedownManager;

    /**
     * Provides a globally accessible reference to the currently running
     * plugin instance. Useful for static access in utility classes.
     *
     * @return the loaded plugin instance
     */
    public static PrisonShakedown getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Load configuration and persistent data
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);

        // Instantiate the core manager
        shakedownManager = new ShakedownManager(this, configManager, dataManager);

        // Register command executor and tab completer
        getCommand("shakedown").setExecutor(shakedownManager);
        getCommand("shakedown").setTabCompleter(shakedownManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(shakedownManager, this);
    }

    @Override
    public void onDisable() {
        // Persist data on shutdown
        if (dataManager != null) {
            dataManager.saveData();
        }
        // no audience resources to close
    }
}


