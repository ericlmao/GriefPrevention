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
    void newClaimOutsideBufferSucceeds()
    {
        CreateClaimResult result = dataStore.createClaim(world, 40, 50, 0, 0, 0, 9, OWNER, null, null, null, true);
        assertTrue(result.succeeded);
        assertFalse(result.tooClose);
    }

    @Test
    void resizeIntoBufferSucceeds()
    {
        Claim other = new Claim(new Location(world, 100, 0, 0), new Location(world, 109, 0, 9), OWNER,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 2L);
        CreateClaimResult result = dataStore.createClaim(world, 20, 109, 0, 0, 0, 9, OWNER, null, other.id, null, true);
        assertTrue(result.succeeded);
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
}
