package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AllayItemPickupTest
{
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VISITOR = UUID.randomUUID();
    private static Server server;
    private GriefPrevention plugin;
    private DataStore dataStore;
    private World world;
    private Claim claim;
    private Allay allay;
    private Item item;
    private EntityEventHandler handler;

    @BeforeAll
    static void beforeAll()
    {
        server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void afterAll()
    {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        plugin = mock(GriefPrevention.class);
        GriefPrevention.instance = plugin;
        plugin.config_claims_bufferRadius = 30;
        plugin.config_claims_preventTheft = true;
        when(plugin.getMinY(world)).thenReturn(-64);
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        dataStore = mock(DataStore.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(new PlayerData()).when(dataStore).getPlayerData(any(UUID.class));
        plugin.dataStore = dataStore;
        claim = new Claim(new Location(world, 0, 0, 0), new Location(world, 9, 0, 9), OWNER,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1L);
        claim.inDataStore = true;
        dataStore.claims.add(claim);
        Method addToChunkClaimMap = DataStore.class.getDeclaredMethod("addToChunkClaimMap", Claim.class);
        addToChunkClaimMap.setAccessible(true);
        addToChunkClaimMap.invoke(dataStore, claim);
        allay = mock(Allay.class);
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(VISITOR);
        // The allay can stand outside while the item remains protected.
        when(allay.getLocation()).thenReturn(new Location(world, -1, 64, 5));
        item = mock(Item.class);
        when(item.getWorld()).thenReturn(world);
        when(item.getLocation()).thenReturn(new Location(world, 0, 64, 5));
        handler = new EntityEventHandler(dataStore, plugin);
    }

    private EntityPickupItemEvent pickUp(LivingEntity entity)
    {
        EntityPickupItemEvent event = new EntityPickupItemEvent(entity, item, 0);
        handler.onEntityPickUpItem(event);
        return event;
    }

    @Test
    void untrustedAllayCannotReachAcrossClaimEdge()
    {
        assertTrue(pickUp(allay).isCancelled());
    }

    @Test
    void unownedAllayCannotCollectProtectedItems()
    {
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(null);
        assertTrue(pickUp(allay).isCancelled());
    }

    @Test
    void offlineOwnerCanKeepCollecting()
    {
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(OWNER);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void containerTrustAllowsCollectionButAccessTrustDoesNot()
    {
        claim.setPermission(VISITOR.toString(), ClaimPermission.Access);
        assertTrue(pickUp(allay).isCancelled());
        claim.setPermission(VISITOR.toString(), ClaimPermission.Container);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void publicContainerTrustAllowsIdentifiedAllays()
    {
        claim.setPermission("public", ClaimPermission.Container);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void bufferProtectsItemsUntilItsOuterEdge()
    {
        when(item.getLocation()).thenReturn(new Location(world, 39, 64, 5));
        assertTrue(pickUp(allay).isCancelled());
        when(item.getLocation()).thenReturn(new Location(world, 40, 64, 5));
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(null);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void subdivisionsHonorRestrictedAndInheritedTrust()
    {
        Claim subdivision = new Claim(new Location(world, 0, 0, 0), new Location(world, 4, 0, 9), null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 2L);
        subdivision.parent = claim;
        subdivision.inDataStore = true;
        claim.children.add(subdivision);
        claim.setPermission(VISITOR.toString(), ClaimPermission.Container);
        assertFalse(pickUp(allay).isCancelled());
        subdivision.setSubclaimRestrictions(true);
        assertTrue(pickUp(allay).isCancelled());
        subdivision.setPermission(VISITOR.toString(), ClaimPermission.Container);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void noteBlockDoesNotGrantTrustAndDoesNotStopTrustedFarmDelivery()
    {
        when(allay.getMemory(MemoryKey.LIKED_NOTEBLOCK_POSITION)).thenReturn(new Location(world, 100, 64, 100));
        assertTrue(pickUp(allay).isCancelled());
        claim.setPermission(VISITOR.toString(), ClaimPermission.Container);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void permissionExtensionsCanDenyTrustedAllayCollection()
    {
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(OWNER);
        PluginManager pluginManager = server.getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof ClaimPermissionCheckEvent check)
                check.setDenialReason(() -> "Denied by extension");
            return null;
        }).when(pluginManager).callEvent(any());
        assertTrue(pickUp(allay).isCancelled());
    }

    @Test
    void lockedDeathDropsStayLockedEvenForOwnersAllay()
    {
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(OWNER);
        Player owner = mock(Player.class);
        when(server.getOfflinePlayer(OWNER)).thenReturn(owner);
        when(owner.isOnline()).thenReturn(true);
        MetadataValue metadata = mock(MetadataValue.class);
        when(metadata.value()).thenReturn(OWNER);
        when(item.getMetadata("GP_ITEMOWNER")).thenReturn(List.of(metadata));
        assertTrue(pickUp(allay).isCancelled());
        dataStore.getPlayerData(OWNER).dropsAreUnlocked = true;
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void disabledWorldAndOtherMobsKeepExistingPickupBehavior()
    {
        assertFalse(pickUp(mock(Player.class)).isCancelled());
        assertFalse(pickUp(mock(LivingEntity.class)).isCancelled());
        assertFalse(pickUp(mock(Monster.class)).isCancelled());
        when(plugin.claimsEnabledForWorld(world)).thenReturn(false);
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void disablingTheftProtectionAllowsCollection()
    {
        plugin.config_claims_preventTheft = false;
        assertFalse(pickUp(allay).isCancelled());
    }

    @Test
    void existingCancellationIsPreserved()
    {
        EntityPickupItemEvent event = new EntityPickupItemEvent(allay, item, 0);
        event.setCancelled(true);
        when(allay.getMemory(MemoryKey.LIKED_PLAYER)).thenReturn(OWNER);
        handler.onEntityPickUpItem(event);
        assertTrue(event.isCancelled());
    }
}
