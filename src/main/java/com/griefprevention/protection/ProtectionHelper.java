package com.griefprevention.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A utility used to simplify various protection-related checks.
 */
public final class ProtectionHelper
{

    private ProtectionHelper() {}

    /**
     * Check the {@link ClaimPermission} state for a {@link Player} at a particular {@link Location}.
     *
     * <p>This respects ignoring claims, wilderness rules, etc.</p>
     *
     * @param player the person performing the action
     * @param location the affected {@link Location}
     * @param permission the required permission
     * @param trigger the triggering {@link Event}, if any
     * @return the denial message supplier, or {@code null} if the action is not denied
     */
    public static @Nullable Supplier<String> checkPermission(
            @NotNull Player player,
            @NotNull Location location,
            @NotNull ClaimPermission permission,
            @Nullable Event trigger)
    {
        World world = location.getWorld();
        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return null;

        PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

        // Administrators ignoring claims always have permission.
        if (playerData.ignoreClaims) return null;

        Claim claim = GriefPrevention.instance.dataStore.getProtectingClaim(location, playerData.lastClaim);

        // If there is no claim here, use wilderness rules.
        if (claim == null)
        {
            ClaimsMode mode = GriefPrevention.instance.config_claims_worldModes.get(world);
            if (mode == ClaimsMode.Creative || mode == ClaimsMode.SurvivalRequiringClaims)
            {
                // Allow placing chest if it would create an automatic claim.
                if (trigger instanceof BlockPlaceEvent placeEvent
                        && placeEvent.getBlock().getType() == Material.CHEST
                        && playerData.getClaims().isEmpty()
                        && GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius > -1)
                    return null;

                // If claims are required, provide relevant information.
                return () ->
                {
                    String reason = GriefPrevention.instance.dataStore.getMessage(Messages.NoBuildOutsideClaims);
                    if (player.hasPermission("griefprevention.ignoreclaims"))
                        reason += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                    reason += "  " + GriefPrevention.instance.dataStore.getMessage(Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
                    return reason;
                };
            }

            // If claims are not required, then the player has permission.
            return null;
        }

        // Update cached claim.
        playerData.lastClaim = claim;

        // Apply claim rules.
        Supplier<String> cancel = withBufferDenial(claim, location, claim.checkPermission(player, permission, trigger));

        // Apply additional specific rules.
        if (cancel != null && trigger instanceof BlockBreakEvent breakEvent)
        {
            PreventBlockBreakEvent preventionEvent = new PreventBlockBreakEvent(breakEvent);
            Bukkit.getPluginManager().callEvent(preventionEvent);
            if (preventionEvent.isCancelled())
            {
                cancel = null;
            }
        }

        return cancel;
    }

    /**
     * Replace a denial with the claim buffer message if the location is outside the claim itself.
     *
     * @param claim the claim protecting the location
     * @param location the affected {@link Location}
     * @param denial the original denial message supplier, if any
     * @return the denial message supplier, or {@code null} if the action is not denied
     */
    public static @Nullable Supplier<String> withBufferDenial(
            @NotNull Claim claim,
            @NotNull Location location,
            @Nullable Supplier<String> denial)
    {
        if (denial == null || claim.contains(location, false, false)) return denial;
        return bufferDenial(claim);
    }

    /**
     * Replace a denial with the claim buffer message if the area does not intersect the claim itself.
     *
     * @param claim the claim protecting the area
     * @param area the affected area
     * @param denial the original denial message supplier, if any
     * @return the denial message supplier, or {@code null} if the action is not denied
     */
    public static @Nullable Supplier<String> withBufferDenial(
            @NotNull Claim claim,
            @NotNull BoundingBox area,
            @Nullable Supplier<String> denial)
    {
        if (denial == null || new BoundingBox(claim).intersects(area)) return denial;
        return bufferDenial(claim);
    }

    private static @NotNull Supplier<String> bufferDenial(@NotNull Claim claim)
    {
        return () -> GriefPrevention.instance.dataStore.getMessage(Messages.NoPermissionNearClaim,
                String.valueOf(GriefPrevention.instance.config_claims_bufferRadius), claim.getOwnerName());
    }

}
