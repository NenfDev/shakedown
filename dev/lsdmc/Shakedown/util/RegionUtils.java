package dev.lsdmc.Shakedown.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.alex9849.arm.AdvancedRegionMarket;
// Avoid importing ARM RegionManager to prevent name clashes; use FQCN
// region manager of ARM is imported by fully qualified name to avoid name clashes
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods bridging WorldGuard, AdvancedRegionMarket and Bukkit. The
 * methods in this class aim to wrap common patterns such as retrieving a
 * region by name, adding or removing a permission group to a region, and
 * resolving the owner of a region. All API calls are wrapped in optional
 * containers to avoid null pointer exceptions.
 */
public final class RegionUtils {
    private RegionUtils() {}

    /**
     * Retrieves a WorldGuard region by its ID within a given world.
     *
     * @param world the world where the region resides
     * @param id    the region ID
     * @return an optional containing the region if found
     */
    public static Optional<ProtectedRegion> getWorldGuardRegionById(World world, String id) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return Optional.empty();
        }
        ProtectedRegion pr = manager.getRegion(id);
        if (pr != null) {
            dev.lsdmc.Shakedown.util.Debug.info("WG region found in world=" + world.getName() + ", id=" + id);
        }
        return Optional.ofNullable(pr);
    }

    /**
     * Attempts to locate a WorldGuard region by ID across all loaded worlds.
     *
     * @param id the region ID
     * @return an optional containing the region if found
     */
    public static Optional<ProtectedRegion> findWorldGuardRegionByIdAnyWorld(String id) {
        for (World world : Bukkit.getWorlds()) {
            Optional<ProtectedRegion> region = getWorldGuardRegionById(world, id);
            if (region.isPresent()) {
                dev.lsdmc.Shakedown.util.Debug.info("WG region found across worlds: id=" + id + ", world=" + world.getName());
                return region;
            }
        }
        return Optional.empty();
    }

    /**
     * Adds a permission group to the member domain of a region. WorldGuard
     * domains manage owners and members separately; this method uses the
     * member list to grant build access temporarily.
     *
     * @param region    the region to modify
     * @param groupName the permission group (LuckPerms group) to add
     */
    public static void addGroupToRegion(ProtectedRegion region, String groupName) {
        if (region == null) return;
        region.getMembers().addGroup(groupName);
    }

    /**
     * Removes a permission group from the member domain of a region.
     *
     * @param region    the region to modify
     * @param groupName the permission group to remove
     */
    public static void removeGroupFromRegion(ProtectedRegion region, String groupName) {
        if (region == null) return;
        region.getMembers().removeGroup(groupName);
    }

    /**
     * Attempts to resolve the owner of a region via AdvancedRegionMarket. If
     * ARM isn’t installed or the region is not managed by ARM, the returned
     * optional will be empty.
     *
     * @param regionId the region ID to query
     * @return the UUID of the owner if available
     */
    public static Optional<java.util.UUID> getRegionOwner(String regionId) {
        try {
            AdvancedRegionMarket arm = getArm();
            if (arm == null) return Optional.empty();
            net.alex9849.arm.regions.RegionManager manager = arm.getRegionManager();
            if (manager == null) return Optional.empty();
            for (World world : Bukkit.getWorlds()) {
                net.alex9849.arm.regions.Region region = manager.getRegionByNameAndWorld(regionId, world.getName());
                if (region != null) {
                    UUID owner = region.getOwner();
                    dev.lsdmc.Shakedown.util.Debug.info("ARM owner lookup: id=" + regionId + ", world=" + world.getName() + ", owner=" + owner);
                    return Optional.ofNullable(owner);
                }
            }
            return Optional.empty();
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * Finds the first ARM region owned by the specified player UUID.
     *
     * @param ownerUuid the owner's UUID
     * @return the region ID if found
     */
    public static Optional<String> findFirstArmRegionOwnedBy(UUID ownerUuid) {
        List<String> ids = listArmRegionsOwnedBy(ownerUuid);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /**
     * Returns a list of ARM-managed region IDs if ARM is present.
     */
    public static List<String> listArmRegionIds() {
        List<String> ids = new ArrayList<>();
        try {
            AdvancedRegionMarket arm = getArm();
            if (arm == null) return ids;
            net.alex9849.arm.regions.RegionManager manager = arm.getRegionManager();
            if (manager == null) return ids;
            // Iterate WG regions per world, translate to ARM regions when possible
            for (World world : Bukkit.getWorlds()) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager wg = container.get(BukkitAdapter.adapt(world));
                if (wg == null) continue;
                for (ProtectedRegion pr : wg.getRegions().values()) {
                    net.alex9849.arm.regions.Region armRegion = manager.getRegionByNameAndWorld(pr.getId(), world.getName());
                    if (armRegion != null) {
                        String id = armRegion.getRegion().getId();
                        if (id != null) ids.add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return ids;
    }

    /**
     * Lists ARM region IDs where the specified player is the owner, renter, or a primary user.
     */
    public static List<String> listArmRegionsOwnedBy(UUID playerUuid) {
        List<String> ids = new ArrayList<>();
        try {
            AdvancedRegionMarket arm = getArm();
            if (arm == null) return ids;
            net.alex9849.arm.regions.RegionManager manager = arm.getRegionManager();
            if (manager == null) return ids;
            java.util.Set<String> uniqueIds = new java.util.HashSet<>();
            // Include owned regions via direct API
            java.util.List<net.alex9849.arm.regions.Region> owned = manager.getRegionsByOwner(playerUuid);
            for (net.alex9849.arm.regions.Region region : owned) {
                String id = region.getRegion().getId();
                if (id != null) uniqueIds.add(id);
            }
            // Include rented/tenant/member regions by scanning WG regions per world
            for (World world : Bukkit.getWorlds()) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager wg = container.get(BukkitAdapter.adapt(world));
                if (wg == null) continue;
                for (ProtectedRegion pr : wg.getRegions().values()) {
                    net.alex9849.arm.regions.Region armRegion = manager.getRegionByNameAndWorld(pr.getId(), world.getName());
                    if (armRegion == null) continue;
                    String id = armRegion.getRegion().getId();
                    if (id == null) continue;
                    // Owner
                    UUID owner = armRegion.getOwner();
                    if (playerUuid.equals(owner)) { uniqueIds.add(id); continue; }
                    // Renter/tenant via reflective helpers
                    UUID renter = tryGetRenterUuid(armRegion);
                    if (playerUuid.equals(renter)) { uniqueIds.add(id); continue; }
                    // Members/Users
                    Set<UUID> members = tryGetMemberUuids(armRegion);
                    if (members.contains(playerUuid)) uniqueIds.add(id);
                }
            }
            ids = new java.util.ArrayList<>(uniqueIds);
            dev.lsdmc.Shakedown.util.Debug.info("ARM regions (owned/rented/members) for uuid=" + playerUuid + ": " + ids.size());
        } catch (Throwable ignored) {}
        return ids;
    }

    /**
     * Attempts to resolve a region's primary user (owner or renter/tenant) by region ID.
     */
    public static Optional<UUID> getRegionPrimaryUser(String regionId) {
        try {
            AdvancedRegionMarket arm = getArm();
            if (arm == null) return Optional.empty();
            net.alex9849.arm.regions.RegionManager manager = arm.getRegionManager();
            if (manager == null) return Optional.empty();
            for (World world : Bukkit.getWorlds()) {
                net.alex9849.arm.regions.Region region = manager.getRegionByNameAndWorld(regionId, world.getName());
                if (region != null) {
                    UUID owner = region.getOwner();
                    if (owner != null) return Optional.of(owner);
                    UUID renter = tryGetRenterUuid(region);
                    if (renter != null) return Optional.of(renter);
                    Set<UUID> members = tryGetMemberUuids(region);
                    if (!members.isEmpty()) return Optional.of(members.iterator().next());
                    return Optional.empty();
                }
            }
            return Optional.empty();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static AdvancedRegionMarket getArm() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("AdvancedRegionMarket");
        if (plugin instanceof AdvancedRegionMarket arm) {
            return arm;
        }
        try {
            return (AdvancedRegionMarket) AdvancedRegionMarket.class.getMethod("getInstance").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String tryGetRegionId(Object armRegion) {
        if (armRegion instanceof net.alex9849.arm.regions.Region r) {
            try {
                return r.getRegion().getId();
            } catch (Throwable ignored) {}
        }
        try {
            // Preferred: region.getRegion().getId()
            Object wgRegion = armRegion.getClass().getMethod("getRegion").invoke(armRegion);
            if (wgRegion != null) {
                Object id = wgRegion.getClass().getMethod("getId").invoke(wgRegion);
                if (id instanceof String s) return s;
            }
        } catch (Throwable ignored) {}
        try {
            // Fallback: region.getRegionId()
            Object id = armRegion.getClass().getMethod("getRegionId").invoke(armRegion);
            if (id instanceof String s) return s;
        } catch (Throwable ignored) {}
        try {
            // Fallback: region.getName()
            Object id = armRegion.getClass().getMethod("getName").invoke(armRegion);
            if (id instanceof String s) return s;
        } catch (Throwable ignored) {}
        return null;
    }

    private static UUID tryGetOwnerUuid(Object armRegion) {
        try {
            // Common: region.getOwner() -> UUID
            Object owner = armRegion.getClass().getMethod("getOwner").invoke(armRegion);
            if (owner instanceof UUID u) return u;
        } catch (Throwable ignored) {}
        try {
            // Alternate: region.getOwnerUUID()
            Object owner = armRegion.getClass().getMethod("getOwnerUUID").invoke(armRegion);
            if (owner instanceof UUID u) return u;
        } catch (Throwable ignored) {}
        try {
            // Alternate: region.getOwnerName() -> resolve
            Object name = armRegion.getClass().getMethod("getOwnerName").invoke(armRegion);
            if (name instanceof String s && !s.isEmpty()) {
                return Bukkit.getOfflinePlayer(s).getUniqueId();
            }
        } catch (Throwable ignored) {}
        try {
            // Alternate: region.getOwnerPlayer() -> OfflinePlayer
            Object p = armRegion.getClass().getMethod("getOwnerPlayer").invoke(armRegion);
            if (p instanceof org.bukkit.OfflinePlayer op) return op.getUniqueId();
        } catch (Throwable ignored) {}
        try {
            // Alternate: region.getOwners() -> Collection<UUID>
            Object owners = armRegion.getClass().getMethod("getOwners").invoke(armRegion);
            if (owners instanceof Iterable<?> iter) {
                for (Object o : iter) {
                    if (o instanceof UUID u) return u;
                    if (o instanceof org.bukkit.OfflinePlayer op) return op.getUniqueId();
                    if (o instanceof String s && !s.isEmpty()) return Bukkit.getOfflinePlayer(s).getUniqueId();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static UUID tryGetRenterUuid(Object armRegion) {
        try {
            Object renter = armRegion.getClass().getMethod("getRenter").invoke(armRegion);
            if (renter instanceof UUID u) return u;
            if (renter instanceof org.bukkit.OfflinePlayer op) return op.getUniqueId();
        } catch (Throwable ignored) {}
        try {
            Object tenant = armRegion.getClass().getMethod("getTenant").invoke(armRegion);
            if (tenant instanceof UUID u) return u;
            if (tenant instanceof org.bukkit.OfflinePlayer op) return op.getUniqueId();
        } catch (Throwable ignored) {}
        try {
            Object lessee = armRegion.getClass().getMethod("getLessee").invoke(armRegion);
            if (lessee instanceof UUID u) return u;
            if (lessee instanceof org.bukkit.OfflinePlayer op) return op.getUniqueId();
        } catch (Throwable ignored) {}
        return null;
    }

    private static Set<UUID> tryGetMemberUuids(Object armRegion) {
        Set<UUID> set = new HashSet<>();
        try {
            Object tenants = armRegion.getClass().getMethod("getTenants").invoke(armRegion);
            addUserLikeToSet(set, tenants);
        } catch (Throwable ignored) {}
        try {
            Object members = armRegion.getClass().getMethod("getMembers").invoke(armRegion);
            addUserLikeToSet(set, members);
        } catch (Throwable ignored) {}
        try {
            Object users = armRegion.getClass().getMethod("getUsers").invoke(armRegion);
            addUserLikeToSet(set, users);
        } catch (Throwable ignored) {}
        return set;
    }

    private static void addUserLikeToSet(Set<UUID> set, Object iterable) {
        if (iterable instanceof Iterable<?> iter) {
            for (Object o : iter) {
                if (o instanceof UUID u) set.add(u);
                else if (o instanceof org.bukkit.OfflinePlayer op) set.add(op.getUniqueId());
                else if (o instanceof String s && !s.isEmpty()) set.add(Bukkit.getOfflinePlayer(s).getUniqueId());
            }
        }
    }

    private static boolean isRegionOwnedBy(Object region, UUID playerUuid) {
        UUID owner = tryGetOwnerUuid(region);
        if (playerUuid.equals(owner)) return true;
        UUID renter = tryGetRenterUuid(region);
        if (playerUuid.equals(renter)) return true;
        Set<UUID> members = tryGetMemberUuids(region);
        return members.contains(playerUuid);
    }
}


