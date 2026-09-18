package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PressurePlateAccessTest
{
    private static final UUID OWNER = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739");
    private static final UUID VISITOR = UUID.fromString("0f6d3a2e-1b4c-4d5e-8f70-123456789abc");
    private GriefPrevention plugin;
    private DataStore dataStore;
    private PlayerEventHandler handler;
    private Player player;
    private PlayerData playerData;
    private World world;
    private Claim claim;

    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        doAnswer(invocation ->
        {
            Tag<Material> tag = mock();
            NamespacedKey key = invocation.getArgument(1);
            when(tag.isTagged(any())).thenAnswer(check -> key.getKey().equals("pressure_plates")
                    && ((Material) check.getArgument(0)).name().endsWith("_PRESSURE_PLATE"));
            return tag;
        }).when(server).getTag(notNull(), notNull(), notNull());
        Bukkit.setServer(server);
        InventoryType.values();
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
        world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(320);
        plugin = mock(GriefPrevention.class);
        plugin.config_claims_preventPressurePlates = true;
        plugin.config_claims_bufferRadius = 30;
        plugin.config_claims_worldModes = new ConcurrentHashMap<>();
        plugin.config_claims_worldModes.put(world, ClaimsMode.Survival);
        plugin.config_pvp_blockedCommands = new ArrayList<>();
        plugin.config_claims_commandsRequiringAccessTrust = new ArrayList<>();
        plugin.config_spam_monitorSlashCommands = new ArrayList<>();
        plugin.config_eavesdrop_whisperCommands = new ArrayList<>();
        when(plugin.claimsEnabledForWorld(world)).thenReturn(true);
        doReturn(Bukkit.getLogger()).when(plugin).getLogger();
        GriefPrevention.instance = plugin;

        dataStore = mock(DataStore.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(Collections.emptyList()).when(dataStore).loadBannedWords();
        playerData = new PlayerData();
        doReturn(playerData).when(dataStore).getPlayerData(any(UUID.class));
        plugin.dataStore = dataStore;
        claim = new Claim(new Location(world, 0, 0, 0), new Location(world, 9, 0, 9), OWNER,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1L);
        claim.inDataStore = true;
        dataStore.claims.add(claim);
        dataStore.claimIDMap.put(1L, claim);
        Method addToChunkClaimMap = DataStore.class.getDeclaredMethod("addToChunkClaimMap", Claim.class);
        addToChunkClaimMap.setAccessible(true);
        addToChunkClaimMap.invoke(dataStore, claim);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(VISITOR);
        handler = new PlayerEventHandler(dataStore, plugin);
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = ".*_PRESSURE_PLATE", mode = EnumSource.Mode.MATCH_ALL)
    void strangersCannotStepOnAnyPlate(Material material)
    {
        assertTrue(step(material, 5).isCancelled());
        assertSame(claim, playerData.lastClaim);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimPermission.class, names = {"Access", "Container", "Build"})
    void trustedPlayersCanStepOnPlates(ClaimPermission permission)
    {
        claim.setPermission(VISITOR.toString(), permission);
        assertFalse(step(Material.OAK_PRESSURE_PLATE, 5).isCancelled());
    }

    @Test
    void ownerAndPublicAccessCanUsePlates()
    {
        when(player.getUniqueId()).thenReturn(OWNER);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
        when(player.getUniqueId()).thenReturn(VISITOR);
        claim.setPermission("public", ClaimPermission.Access);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
    }

    @Test
    void administratorBypassRequiresExistingPermission()
    {
        playerData.ignoreClaims = true;
        assertTrue(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
        when(player.hasPermission("griefprevention.ignoreclaims")).thenReturn(true);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
    }

    @Test
    void subdivisionRestrictionsOverrideParentTrust()
    {
        Claim subdivision = new Claim(new Location(world, 2, 0, 2), new Location(world, 7, 0, 7), null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 2L);
        subdivision.parent = claim;
        subdivision.inDataStore = true;
        claim.children.add(subdivision);
        claim.setPermission(VISITOR.toString(), ClaimPermission.Access);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
        assertSame(subdivision, playerData.lastClaim);
        subdivision.setSubclaimRestrictions(true);
        assertTrue(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
        subdivision.setPermission(VISITOR.toString(), ClaimPermission.Access);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
    }

    @Test
    void bufferUsesClaimTrustAndWildernessRemainsOpen()
    {
        assertTrue(step(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 39).isCancelled());
        assertFalse(step(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 40).isCancelled());
        claim.setPermission(VISITOR.toString(), ClaimPermission.Access);
        assertFalse(step(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 39).isCancelled());
    }

    @Test
    void configurationAndDisabledWorldRestorePlayerActivation()
    {
        plugin.config_claims_preventPressurePlates = false;
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
        plugin.config_claims_preventPressurePlates = true;
        when(plugin.claimsEnabledForWorld(world)).thenReturn(false);
        assertFalse(step(Material.STONE_PRESSURE_PLATE, 5).isCancelled());
    }

    @Test
    void turtleEggsStillRequireBuildAndFarmlandUsesSeparateHandler()
    {
        claim.setPermission(VISITOR.toString(), ClaimPermission.Access);
        assertTrue(step(Material.TURTLE_EGG, 5).isCancelled());
        plugin.config_claims_preventPressurePlates = false;
        assertTrue(step(Material.TURTLE_EGG, 5).isCancelled());
        claim.setPermission(VISITOR.toString(), ClaimPermission.Build);
        assertFalse(step(Material.TURTLE_EGG, 5).isCancelled());
        assertFalse(step(Material.FARMLAND, 5).isCancelled());
    }

    @Test
    void nonPlayerPlateActivationRemainsUnchanged()
    {
        EntityInteractEvent event = new EntityInteractEvent(mock(Entity.class), block(Material.OAK_PRESSURE_PLATE, 5));
        new EntityEventHandler(dataStore, plugin).onEntityInteract(event);
        assertFalse(event.isCancelled());
    }

    private PlayerInteractEvent step(Material material, int x)
    {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.PHYSICAL, null, block(material, x), BlockFace.UP);
        handler.onPlayerInteract(event);
        return event;
    }

    private Block block(Material material, int x)
    {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, x, 64, 5));
        return block;
    }
}
