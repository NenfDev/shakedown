package dev.lsdmc.Shakedown.shakedown;

import dev.lsdmc.Shakedown.config.ConfigManager;
import dev.lsdmc.Shakedown.data.DataManager;
import dev.lsdmc.Shakedown.util.RegionUtils;
import dev.lsdmc.Shakedown.util.Debug;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
// duplicate import removed
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

 

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central class responsible for coordinating shakedown logic. Implements
 * {@link CommandExecutor} and {@link TabCompleter} to handle the `/shakedown`
 * command, and registers event listeners for cleanup tasks. The scanning
 * routines run on a separate virtual thread via a simple executor to keep
 * the server thread responsive.
 */
public class ShakedownManager implements CommandExecutor, TabCompleter, Listener {
    private final DataManager dataManager;
    private ConfigManager config;
    private ContrabandChecker contrabandChecker;
    private final org.bukkit.plugin.Plugin plugin;
    // Track ongoing shakedowns by region ID to prevent concurrent runs
    private final List<String> activeShakedowns;
    private final MiniMessage mini;

    public ShakedownManager(org.bukkit.plugin.Plugin plugin,
                            ConfigManager config,
                            DataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.contrabandChecker = new ContrabandChecker(config);
        this.mini = MiniMessage.miniMessage();
        this.activeShakedowns = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Handles execution of the /shakedown command. Supported usages:
     * `/shakedown <regionId>` - runs a shakedown on the specified region.
     * `/shakedown <player>` - runs a shakedown on the first region owned by the player.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shakedown.admin")) {
                sender.sendMessage(mini.deserialize("<red>You don't have permission to reload.</red>"));
                return true;
            }
            plugin.reloadConfig();
            this.config = new ConfigManager(plugin);
            this.contrabandChecker = new ContrabandChecker(this.config);
            Debug.info("Reload complete: cooldown=" + config.getCooldown() + ", group=" + config.getGuardGroup());
            sender.sendMessage(mini.deserialize("<#51CF66>Config reloaded.</#51CF66>"));
            return true;
        }
        if (!(sender instanceof Player guard)) {
            sender.sendMessage(mini.deserialize("<red>Only players can execute shakedowns.</red>"));
            return true;
        }
        if (!guard.hasPermission("shakedown.use")) {
            guard.sendMessage(mini.deserialize("<red>You don't have permission to use this command.</red>"));
            return true;
        }
        if (args.length < 1) {
            guard.sendMessage(mini.deserialize("<red>Usage: /shakedown <regionId|player|reload></red>"));
            return true;
        }
        String targetArg = args[0];
        // Determine whether the argument is a player name or a region ID
        Debug.info("/shakedown invoked by=" + sender.getName() + ", arg0=" + targetArg);
        Player prisoner = Bukkit.getPlayerExact(targetArg);
        ProtectedRegion region = null;
        if (prisoner != null) {
            // Find a region owned by the player via ARM or fallback to name match
            String regionId = findRegionOwnedBy(prisoner.getUniqueId());
            if (regionId == null) {
                Debug.warn("No ARM region found for player=" + prisoner.getName() + " (" + prisoner.getUniqueId() + ")");
                guard.sendMessage(mini.deserialize("<red>No ARM region owned by that player.</red>"));
                return true;
            }
            Debug.info("Resolved player region id=" + regionId);
            region = RegionUtils.findWorldGuardRegionByIdAnyWorld(regionId).orElse(null);
        } else {
            // treat as region id; get region in guard’s world
            region = RegionUtils.findWorldGuardRegionByIdAnyWorld(targetArg).orElse(null);
            // Require region to be ARM-managed and resolve primary user (owner/renter)
            java.util.Optional<java.util.UUID> ownerOpt = RegionUtils.getRegionPrimaryUser(targetArg);
            if (ownerOpt.isEmpty()) {
                Debug.warn("Region not ARM-managed or has no primary user: id=" + targetArg);
                guard.sendMessage(mini.deserialize("<red>That region is not managed by ARM or has no owner.</red>"));
                return true;
            }
            Player ownerOnline = Bukkit.getPlayer(ownerOpt.get());
            if (ownerOnline == null) {
                Debug.warn("Region owner/renter offline for region=" + targetArg + ", owner=" + ownerOpt.get());
                guard.sendMessage(mini.deserialize("<red>The region owner must be online for a shakedown.</red>"));
                return true;
            }
            Debug.info("Resolved region to player=" + ownerOnline.getName() + " (" + ownerOnline.getUniqueId() + ")");
            prisoner = ownerOnline;
        }
        if (region == null || prisoner == null) {
            Debug.warn("Failed to resolve region or prisoner. region=" + (region == null ? "null" : region.getId()) + ", prisoner=" + (prisoner == null ? "null" : prisoner.getName()));
            guard.sendMessage(mini.deserialize("<red>Unable to locate region or prisoner.</red>"));
            return true;
        }
        // Check cooldown – keyed by prisoner UUID; if staff has admin permission they can bypass
        long last = dataManager.getLastShakedown(prisoner.getUniqueId().toString());
        Duration cooldown = config.getCooldown();
        long now = Instant.now().getEpochSecond();
        if (!guard.hasPermission("shakedown.admin") && last > 0) {
            long diffSeconds = now - last;
            if (diffSeconds < cooldown.toSeconds()) {
                long remaining = cooldown.toSeconds() - diffSeconds;
                long hrs = remaining / 3600;
                long mins = (remaining % 3600) / 60;
                Debug.info("Cooldown active for prisoner=" + prisoner.getUniqueId() + ", remaining=" + remaining + "s");
                guard.sendMessage(mini.deserialize("<red>This player has been shaken down recently. Try again in <white>" + hrs + "h " + mins + "m</white>.</red>"));
                return true;
            }
        }
        // Prevent concurrent shakedowns for same region
        if (activeShakedowns.contains(region.getId())) {
            guard.sendMessage(mini.deserialize("<red>A shakedown is already in progress for this region.</red>"));
            return true;
        }
        activeShakedowns.add(region.getId());
        Player finalPrisoner = prisoner;
        ProtectedRegion finalRegion = region;
        // Announce the shakedown using Adventure
        broadcastShakedownStart(guard, finalPrisoner, finalRegion);
        // Run search synchronously on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Debug.info("Starting shakedown in region=" + finalRegion.getId() + " for prisoner=" + finalPrisoner.getName());
                performShakedown(guard, finalPrisoner, finalRegion);
            } catch (Throwable ex) {
                guard.sendMessage(mini.deserialize("<red>An error occurred during the shakedown: <white>" + ex.getMessage() + "</white></red>"));
                Debug.error("Exception during shakedown: " + ex.getMessage(), ex);
                ex.printStackTrace();
            } finally {
                activeShakedowns.remove(finalRegion.getId());
                // Ensure group cleanup even on error
                try { RegionUtils.removeGroupFromRegion(finalRegion, config.getGuardGroup()); } catch (Throwable ignored) {}
                Debug.info("Shakedown finished for region=" + finalRegion.getId());
            }
        });
        return true;
    }

    /**
     * Performs the shakedown by scanning every block inside the region and
     * collecting tasks to remove contraband on the main thread. At the end of
     * the scan the removal tasks are executed synchronously and the guard is
     * rewarded or the prisoner punished accordingly.
     */
    private void performShakedown(Player guard, Player prisoner, ProtectedRegion region) {
        World world = prisoner.getWorld();
        int minX = region.getMinimumPoint().getBlockX();
        int minY = region.getMinimumPoint().getBlockY();
        int minZ = region.getMinimumPoint().getBlockZ();
        int maxX = region.getMaximumPoint().getBlockX();
        int maxY = region.getMaximumPoint().getBlockY();
        int maxZ = region.getMaximumPoint().getBlockZ();

        List<Runnable> removalTasks = new ArrayList<>();
        List<ItemStack> foundContraband = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = world.getBlockAt(loc);
                    Material type = block.getType();
                    // Remove contraband crops (e.g., sugar cane, bamboo, etc.)
                    if (contrabandChecker.isContraband(new ItemStack(type))) {
                        removalTasks.add(() -> block.setType(Material.AIR));
                    }
                    // Check chests and other containers for contraband items
                    if (block.getState() instanceof Container container) {
                        Inventory inv = container.getInventory();
                        for (int slot = 0; slot < inv.getSize(); slot++) {
                            ItemStack item = inv.getItem(slot);
                            if (contrabandChecker.isContraband(item)) {
                                // capture for removal
                                int index = slot;
                                removalTasks.add(() -> {
                                    ItemStack removed = inv.getItem(index);
                                    if (removed != null) {
                                        foundContraband.add(removed.clone());
                                        inv.setItem(index, null);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
        // Execute removal tasks (already on main thread)
        removalTasks.forEach(Runnable::run);
        // After modifications, reward or punish
        postShakedown(guard, prisoner, region, foundContraband);
    }

    /**
     * Called once the search and removal tasks complete. Persists the
     * shakedown timestamp, runs configured reward or punishment commands,
     * broadcasts results and removes the temporary guard group from the region.
     */
    private void postShakedown(Player guard, Player prisoner, ProtectedRegion region, List<ItemStack> contrabandFound) {
        // Persist cooldown
        dataManager.recordShakedown(prisoner.getUniqueId().toString());
        // Remove guard group from region to restore access control
        RegionUtils.removeGroupFromRegion(region, config.getGuardGroup());
        // Build summary of removed items
        if (!contrabandFound.isEmpty()) {
            Debug.info("Contraband count=" + contrabandFound.size());
            String summary = contrabandFound.stream()
                    .collect(Collectors.groupingBy(ItemStack::getType, Collectors.summingInt(ItemStack::getAmount)))
                    .entrySet().stream()
                    .map(e -> e.getValue() + "x " + e.getKey().name().toLowerCase())
                    .collect(Collectors.joining(", "));
            // Notify guard and prisoner
            guard.sendMessage(mini.deserialize("<gold>Contraband found: <white>" + summary + "</white></gold>"));
            prisoner.sendMessage(mini.deserialize("<red>Contraband has been confiscated from your cell.</red>"));
            // Run punishment commands
            for (String cmd : config.getPunishmentCommands()) {
                String parsed = cmd.replace("{player}", prisoner.getName()).replace("{guard}", guard.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        } else {
            guard.sendMessage(mini.deserialize("<green>No contraband found.</green>"));
            // Reward guard for fair search
            for (String cmd : config.getNoContrabandCommands()) {
                String parsed = cmd.replace("{player}", prisoner.getName()).replace("{guard}", guard.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
        // Always run reward commands on success of search (contraband or not)
        for (String cmd : config.getRewardCommands()) {
            String parsed = cmd.replace("{player}", prisoner.getName()).replace("{guard}", guard.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    /**
     * Broadcasts the start of a shakedown to the entire server using
     * Adventure. Also sets a title message for the targeted prisoner to
     * indicate that their cell is being searched.
     */
    private void broadcastShakedownStart(Player guard, Player prisoner, ProtectedRegion region) {
        String regionId = region.getId();
        // Add guard group to region at start
        RegionUtils.addGroupToRegion(region, config.getGuardGroup());
        // Compose broadcast
        Component broadcast = mini.deserialize(
                "!<#9D4EDD><bold>Shakedown</bold></#9D4EDD> <#ADB5BD>»</#ADB5BD> <#FF6B6B>Region </#FF6B6B><white>" +
                        regionId + "</white> <#ADB5BD>[</#ADB5BD><#06FFA5>" + prisoner.getName() + "</#06FFA5><#ADB5BD>]</#ADB5BD>");
        org.bukkit.Bukkit.getServer().sendMessage(broadcast);
        // Title message to prisoner
        Component title = mini.deserialize("<#9D4EDD><bold>Shakedown</bold></#9D4EDD>");
        Component subtitle = mini.deserialize("<#FF6B6B>Your cell is being searched!</#FF6B6B>");
        Title.Times times = Title.Times.times(java.time.Duration.ofSeconds(0), java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(2));
        prisoner.showTitle(Title.title(title, subtitle, times));
    }

    /**
     * Attempts to find a region owned by the given player UUID using ARM. If
     * ARM is unavailable or the player owns multiple regions, the first match
     * will be returned. Returns null if none found.
     */
    @Nullable
    private String findRegionOwnedBy(UUID owner) {
        List<String> regions = dev.lsdmc.Shakedown.util.RegionUtils.listArmRegionsOwnedBy(owner);
        Debug.info("ARM regions for uuid=" + owner + ": " + regions.size());
        return regions.isEmpty() ? null : regions.get(0);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            // Suggest online players and loaded region names
            List<String> suggestions = new ArrayList<>();
            suggestions.add("reload");
            // Player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestions.add(p.getName());
            }
            // ARM region IDs if available
            suggestions.addAll(RegionUtils.listArmRegionIds());
            return suggestions;
        }
        return Collections.emptyList();
    }

    // Event handlers could be extended to cleanup or cancel shakedowns if necessary
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // If the targeted prisoner logs out during a shakedown, record timestamp to prevent abuse
        Player player = event.getPlayer();
        dataManager.recordShakedown(player.getUniqueId().toString());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Prevent guards from breaking blocks outside of removal tasks
        if (activeShakedowns.isEmpty()) return;
        // Additional logic can be added here to restrict breaking outside of search area
    }
}