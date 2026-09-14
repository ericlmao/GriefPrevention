package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ClaimBufferTest
{
    private static final UUID OWNER = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739");

    private World world;
    private DataStore dataStore;
    private Claim existing;

    @BeforeAll
    static void beforeAll()
    {
        Bukkit.setServer(ServerMocks.newServer());
    }

    @AfterAll
    static void afterAll()
    {
        ServerMocks.unsetBukkitServer();
        GriefPrevention.instance = null;
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        WorldBorder border = mock(WorldBorder.class);
        when(border.isInside(any())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);

        GriefPrevention plugin = mock(GriefPrevention.class);
        plugin.config_claims_worldModes = new ConcurrentHashMap<>();
        plugin.config_claims_bufferRadius = 30;
        when(plugin.getMinY(world)).thenReturn(-64);
        GriefPrevention.instance = plugin;

        dataStore = mock(DataStore.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));

        // Existing claim spanning x/z 0 to 9.
        existing = new Claim(new Location(world, 0, 0, 0), new Location(world, 9, 0, 9), OWNER,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1L);
        existing.inDataStore = true;
        dataStore.claims.add(existing);
        dataStore.claimIDMap.put(1L, existing);
        Method addToChunkClaimMap = DataStore.class.getDeclaredMethod("addToChunkClaimMap", Claim.class);
        addToChunkClaimMap.setAccessible(true);
        addToChunkClaimMap.invoke(dataStore, existing);
    }

    @Test
    void locationWithinBufferIsProtected()
    {
        assertEquals(existing, dataStore.getClaimNear(new Location(world, 39, 64, 5)));
        assertEquals(existing, dataStore.getProtectingClaim(new Location(world, -30, 64, -30), null));
    }

    @Test
    void locationOutsideBufferIsNotProtected()
    {
        assertNull(dataStore.getClaimNear(new Location(world, 40, 64, 5)));
        assertNull(dataStore.getProtectingClaim(new Location(world, -31, 64, 5), null));
    }

    @Test
    void bufferDisabledWhenRadiusZero()
    {
        GriefPrevention.instance.config_claims_bufferRadius = 0;
        assertNull(dataStore.getClaimNear(new Location(world, 10, 64, 5)));
    }

    @Test
    void newClaimWithinBufferFails()
    {
        CreateClaimResult result = dataStore.createClaim(world, 39, 49, 0, 0, 0, 9, OWNER, null, null, null, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
        assertEquals(existing, result.claim);
    }

    @Test
    void newClaimNeedsSixtyBlockGap()
    {
        // Existing claim ends at x = 9. Starting at x = 69 leaves a 59 block gap, x = 70 leaves 60.
        CreateClaimResult tooClose = dataStore.createClaim(world, 69, 79, 0, 0, 0, 9, OWNER, null, null, null, true);
        assertFalse(tooClose.succeeded);
        assertTrue(tooClose.tooClose);
        assertEquals(existing, tooClose.claim);

        CreateClaimResult farEnough = dataStore.createClaim(world, 70, 80, 0, 0, 0, 9, OWNER, null, null, null, true);
        assertTrue(farEnough.succeeded);
        assertFalse(farEnough.tooClose);
    }


    @Test
    void protectedBoundsIncludeBuffer()
    {
        me.ryanhamshire.GriefPrevention.util.BoundingBox bounds = DataStore.getProtectedBounds(existing);
        assertEquals(-30, bounds.getMinX());
        assertEquals(39, bounds.getMaxX());
        assertEquals(-30, bounds.getMinZ());
        assertEquals(39, bounds.getMaxZ());
    }

    @Test
    void subdivisionHasNoBuffer()
    {
        Claim subdivision = new Claim(new Location(world, 2, 0, 2), new Location(world, 4, 0, 4), null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 3L);
        subdivision.parent = existing;
        me.ryanhamshire.GriefPrevention.util.BoundingBox bounds = DataStore.getProtectedBounds(subdivision);
        assertEquals(2, bounds.getMinX());
        assertEquals(4, bounds.getMaxX());
    }

    @Test
    void cachedSubdivisionResolvesToParentBuffer()
    {
        Claim subdivision = new Claim(new Location(world, 2, 0, 2), new Location(world, 4, 0, 4), null,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 3L);
        subdivision.parent = existing;
        assertEquals(existing, dataStore.getClaimNear(new Location(world, 20, 64, 5), subdivision));
    }

    @Test
    void locationBelowClaimIsNotProtected()
    {
        assertNull(dataStore.getClaimNear(new Location(world, 20, -1, 5)));
    }

    @Test
    void bufferDenialReplacesMessageOutsideClaim()
    {
        java.util.function.Supplier<String> denial = () -> "original";
        assertNull(com.griefprevention.protection.ProtectionHelper.withBufferDenial(existing, new Location(world, 20, 64, 5), null));
        assertEquals(denial, com.griefprevention.protection.ProtectionHelper.withBufferDenial(existing, new Location(world, 5, 64, 5), denial));
        assertTrue(denial != com.griefprevention.protection.ProtectionHelper.withBufferDenial(existing, new Location(world, 20, 64, 5), denial));
    }

    @Test
    void bufferDenialForAreaOnlyWhenOutsideClaim()
    {
        java.util.function.Supplier<String> denial = () -> "original";
        me.ryanhamshire.GriefPrevention.util.BoundingBox inside = new me.ryanhamshire.GriefPrevention.util.BoundingBox(8, 64, 8, 12, 70, 12);
        me.ryanhamshire.GriefPrevention.util.BoundingBox outside = new me.ryanhamshire.GriefPrevention.util.BoundingBox(15, 64, 15, 20, 70, 20);
        assertEquals(denial, com.griefprevention.protection.ProtectionHelper.withBufferDenial(existing, inside, denial));
        assertTrue(denial != com.griefprevention.protection.ProtectionHelper.withBufferDenial(existing, outside, denial));
    }

    @Test
    void ignoreClaimsPlayerBypassesBufferOnCreate()
    {
        org.bukkit.entity.Player admin = mock(org.bukkit.entity.Player.class);
        when(admin.getUniqueId()).thenReturn(OWNER);
        PlayerData adminData = mock(PlayerData.class);
        adminData.ignoreClaims = true;
        org.mockito.Mockito.doReturn(adminData).when(dataStore).getPlayerData(OWNER);

        CreateClaimResult result = dataStore.createClaim(world, 39, 49, 0, 0, 0, 9, OWNER, null, null, admin, true);
        assertTrue(result.succeeded);
        assertFalse(result.tooClose);
    }

    @Test
    void normalPlayerCannotBypassBufferOnCreate()
    {
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getUniqueId()).thenReturn(OWNER);
        PlayerData playerData = mock(PlayerData.class);
        org.mockito.Mockito.doReturn(playerData).when(dataStore).getPlayerData(OWNER);

        CreateClaimResult result = dataStore.createClaim(world, 39, 49, 0, 0, 0, 9, OWNER, null, null, player, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
    }

    private Claim addClaim(long id, int x1, int z1, int x2, int z2, UUID owner) throws ReflectiveOperationException
    {
        Claim claim = new Claim(new Location(world, x1, 0, z1), new Location(world, x2, 0, z2), owner,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), id);
        claim.inDataStore = true;
        dataStore.claims.add(claim);
        dataStore.claimIDMap.put(id, claim);
        Method addToChunkClaimMap = DataStore.class.getDeclaredMethod("addToChunkClaimMap", Claim.class);
        addToChunkClaimMap.setAccessible(true);
        addToChunkClaimMap.invoke(dataStore, claim);
        return claim;
    }

    @Test
    void edgeDistanceUsesLargerAxis()
    {
        assertEquals(0, existing.getEdgeDistance(new Location(world, 5, 64, 5)));
        assertEquals(1, existing.getEdgeDistance(new Location(world, 10, 64, 5)));
        assertEquals(4, existing.getEdgeDistance(new Location(world, 12, 64, 13)));
        assertEquals(3, existing.getEdgeDistance(new Location(world, -3, 64, 0)));
    }

    @Test
    void nearestClaimWinsInOverlappingBuffers() throws ReflectiveOperationException
    {
        Claim neighbor = addClaim(2L, 30, 0, 39, 9, UUID.randomUUID());
        assertEquals(existing, dataStore.getClaimNear(new Location(world, 12, 64, 5)));
        assertEquals(neighbor, dataStore.getClaimNear(new Location(world, 27, 64, 5)));
    }

    @Test
    void cachedClaimDoesNotOverrideNearest() throws ReflectiveOperationException
    {
        Claim neighbor = addClaim(2L, 30, 0, 39, 9, UUID.randomUUID());
        assertEquals(existing, dataStore.getClaimNear(new Location(world, 12, 64, 5), neighbor));
        assertEquals(existing, dataStore.getProtectingClaim(new Location(world, 12, 64, 5), neighbor));
    }

    @Test
    void tieGoesToBiggerClaim() throws ReflectiveOperationException
    {
        // x = 20 is 11 blocks from both claims.
        Claim bigger = addClaim(2L, 31, 0, 50, 9, UUID.randomUUID());
        assertEquals(bigger, dataStore.getClaimNear(new Location(world, 20, 64, 5)));
    }

    @Test
    void tieWithEqualSizeGoesToOlderClaim() throws ReflectiveOperationException
    {
        addClaim(2L, 31, 0, 40, 9, UUID.randomUUID());
        assertEquals(existing, dataStore.getClaimNear(new Location(world, 20, 64, 5)));
    }

    @Test
    void biggerAndOlderClaimCanResizeIntoBuffer() throws ReflectiveOperationException
    {
        addClaim(2L, 100, 0, 104, 4, UUID.randomUUID());
        CreateClaimResult result = dataStore.createClaim(world, 0, 60, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertTrue(result.succeeded);
    }

    @Test
    void biggerButNewerClaimCannotResizeIntoBuffer() throws ReflectiveOperationException
    {
        addClaim(2L, 100, 0, 149, 49, UUID.randomUUID());
        CreateClaimResult result = dataStore.createClaim(world, 50, 149, 0, 0, 0, 49, OWNER, null, 2L, null, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
        assertEquals(existing, result.claim);
    }

    @Test
    void olderButSmallerClaimCannotResizeIntoBuffer() throws ReflectiveOperationException
    {
        Claim bigger = addClaim(2L, 100, 0, 149, 49, UUID.randomUUID());
        CreateClaimResult result = dataStore.createClaim(world, 0, 60, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
        assertEquals(bigger, result.claim);
    }

    @Test
    void equalSizeClaimCannotResizeIntoBuffer() throws ReflectiveOperationException
    {
        addClaim(2L, 100, 0, 109, 9, UUID.randomUUID());
        CreateClaimResult result = dataStore.createClaim(world, 0, 50, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
    }

    @Test
    void adminClaimCanResizeIntoPlayerBuffer() throws ReflectiveOperationException
    {
        addClaim(2L, 100, 0, 104, 4, null);
        CreateClaimResult result = dataStore.createClaim(world, 50, 104, 0, 0, 0, 4, null, null, 2L, null, true);
        assertTrue(result.succeeded);
    }

    @Test
    void playerClaimCannotResizeIntoAdminBuffer() throws ReflectiveOperationException
    {
        Claim admin = addClaim(2L, 100, 0, 104, 4, null);
        CreateClaimResult result = dataStore.createClaim(world, 0, 60, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertFalse(result.succeeded);
        assertTrue(result.tooClose);
        assertEquals(admin, result.claim);
    }

    @Test
    void outrankingClaimStillCannotOverlapNeighbor() throws ReflectiveOperationException
    {
        Claim neighbor = addClaim(2L, 100, 0, 104, 4, UUID.randomUUID());
        CreateClaimResult result = dataStore.createClaim(world, 0, 102, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertFalse(result.succeeded);
        assertFalse(result.tooClose);
        assertEquals(neighbor, result.claim);
    }

    @Test
    void alreadyCloseClaimsCanResizeWithoutGettingCloser() throws ReflectiveOperationException
    {
        addClaim(2L, 30, 0, 39, 9, UUID.randomUUID());

        // Growing away from the neighbor keeps the same gap.
        CreateClaimResult away = dataStore.createClaim(world, -20, 9, 0, 0, 0, 20, OWNER, null, 1L, null, true);
        assertTrue(away.succeeded);

        // Growing toward the neighbor makes the gap smaller.
        CreateClaimResult toward = dataStore.createClaim(world, 0, 15, 0, 0, 0, 9, OWNER, null, 1L, null, true);
        assertFalse(toward.succeeded);
        assertTrue(toward.tooClose);
    }

    @Test
    void ignoreClaimsPlayerBypassesBufferOnResize() throws ReflectiveOperationException
    {
        addClaim(2L, 100, 0, 149, 49, UUID.randomUUID());
        org.bukkit.entity.Player admin = mock(org.bukkit.entity.Player.class);
        when(admin.getUniqueId()).thenReturn(OWNER);
        PlayerData adminData = mock(PlayerData.class);
        adminData.ignoreClaims = true;
        org.mockito.Mockito.doReturn(adminData).when(dataStore).getPlayerData(OWNER);

        CreateClaimResult result = dataStore.createClaim(world, 50, 149, 0, 0, 0, 49, OWNER, null, 2L, admin, true);
        assertTrue(result.succeeded);
    }
}
