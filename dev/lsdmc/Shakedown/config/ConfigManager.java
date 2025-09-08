package dev.lsdmc.Shakedown.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Handles reading and exposing values from the plugin’s configuration file.
 * Configuration options are namespaced into logical sections (contraband,
 * rewards, punishments, shakedown) to provide a structured layout.
 */
public final class ConfigManager {
    private final Plugin plugin;
    private final List<String> contrabandMaterials;
    private final List<String> contrabandCustomData;
    private final List<String> rewardCommands;
    private final List<String> punishmentCommands;
    private final List<String> noContrabandCommands;
    private final Duration cooldown;
    private final String guardGroup;
    private final boolean debugEnabled;
    private final boolean debugVerbose;
    private final boolean debugConsole;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Load contraband definitions
        ConfigurationSection contrabandSection = config.getConfigurationSection("contraband");
        if (contrabandSection != null) {
            contrabandMaterials = new ArrayList<>(contrabandSection.getStringList("materials"));
            contrabandCustomData = new ArrayList<>(contrabandSection.getStringList("custom-model-data"));
        } else {
            contrabandMaterials = new ArrayList<>();
            contrabandCustomData = new ArrayList<>();
        }

        // Load reward commands executed when a guard successfully completes a
        // shakedown. Placeholders %guard% and %player% are replaced at runtime.
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            rewardCommands = new ArrayList<>(rewardsSection.getStringList("success.commands"));
        } else {
            rewardCommands = Collections.emptyList();
        }

        // Load punishments executed when contraband is found
        ConfigurationSection punishSection = config.getConfigurationSection("punishments");
        if (punishSection != null) {
            punishmentCommands = new ArrayList<>(punishSection.getStringList("contraband-found.commands"));
            noContrabandCommands = new ArrayList<>(punishSection.getStringList("no-contraband.commands"));
        } else {
            punishmentCommands = Collections.emptyList();
            noContrabandCommands = Collections.emptyList();
        }

        // Shakedown options
        ConfigurationSection shakedown = config.getConfigurationSection("shakedown");
        if (shakedown != null) {
            String cooldownString = shakedown.getString("cooldown", "48h");
            cooldown = parseDuration(cooldownString);
            guardGroup = Objects.requireNonNullElse(shakedown.getString("region-group"), "guards");
        } else {
            cooldown = java.time.Duration.ofHours(48);
            guardGroup = "guards";
        }

        // Debug options
        ConfigurationSection debug = config.getConfigurationSection("debug");
        if (debug != null) {
            debugEnabled = debug.getBoolean("enabled", false);
            debugVerbose = debug.getBoolean("verbose", false);
            debugConsole = debug.getBoolean("console", true);
        } else {
            debugEnabled = false;
            debugVerbose = false;
            debugConsole = true;
        }
    }

    /**
     * Parses a duration string such as "48h", "5m" or "2d" into a
     * {@link Duration} instance. If parsing fails the fallback of 48 hours is
     * returned.
     */
    private Duration parseDuration(String input) {
        try {
            if (input == null) {
                return Duration.ofHours(48);
            }
            input = input.trim().toLowerCase(Locale.ROOT);
            long value = Long.parseLong(input.replaceAll("[^0-9]", ""));
            if (input.endsWith("ms")) {
                return Duration.ofMillis(value);
            } else if (input.endsWith("s")) {
                return Duration.ofSeconds(value);
            } else if (input.endsWith("m")) {
                return Duration.ofMinutes(value);
            } else if (input.endsWith("h")) {
                return Duration.ofHours(value);
            } else if (input.endsWith("d")) {
                return Duration.ofDays(value);
            } else {
                // Default to hours if no unit
                return Duration.ofHours(value);
            }
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Invalid cooldown format in config.yml. Falling back to 48h.");
            return Duration.ofHours(48);
        }
    }

    public List<String> getContrabandMaterials() {
        return Collections.unmodifiableList(contrabandMaterials);
    }

    public List<String> getContrabandCustomData() {
        return Collections.unmodifiableList(contrabandCustomData);
    }

    public List<String> getRewardCommands() {
        return Collections.unmodifiableList(rewardCommands);
    }

    public List<String> getPunishmentCommands() {
        return Collections.unmodifiableList(punishmentCommands);
    }

    public List<String> getNoContrabandCommands() {
        return Collections.unmodifiableList(noContrabandCommands);
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public String getGuardGroup() {
        return guardGroup;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isDebugVerbose() {
        return debugVerbose;
    }

    public boolean isDebugConsole() {
        return debugConsole;
    }
}


