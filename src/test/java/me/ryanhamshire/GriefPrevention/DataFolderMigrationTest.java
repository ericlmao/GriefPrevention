package me.ryanhamshire.GriefPrevention;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataFolderMigrationTest
{
    @BeforeAll
    static void beforeAll()
    {
        Server server = ServerMocks.newServer();
        when(server.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        Bukkit.setServer(server);
        GriefPrevention.instance = mock(GriefPrevention.class);
    }

    @AfterAll
    static void afterAll()
    {
        ServerMocks.unsetBukkitServer();
        GriefPrevention.instance = null;
    }

    @Test
    void dataPathsLiveUnderStorage()
    {
        assertTrue(DataStore.dataLayerFolderPath.startsWith("storage" + File.separator));
        assertTrue(DataStore.configFilePath.startsWith(DataStore.dataLayerFolderPath));
        assertTrue(DataStore.playerDataFolderPath.startsWith(DataStore.dataLayerFolderPath));
        assertFalse(DataStore.dataLayerFolderPath.startsWith("plugins"));
    }

    @Test
    void movesWholeTreeIntoNewFolder(@TempDir Path root) throws Exception
    {
        Path old = root.resolve("plugins").resolve("GriefPreventionData");
        Files.createDirectories(old.resolve("ClaimData"));
        Files.writeString(old.resolve("config.yml"), "test: true");
        Files.writeString(old.resolve("ClaimData").resolve("1.yml"), "claim");
        File target = root.resolve("storage").resolve("GriefPrevention").toFile();

        assertTrue(DataStore.moveDataFolder(old.toFile(), target));

        assertFalse(Files.exists(old));
        assertEquals("test: true", Files.readString(target.toPath().resolve("config.yml")));
        assertEquals("claim", Files.readString(target.toPath().resolve("ClaimData").resolve("1.yml")));
    }

    @Test
    void doesNothingWithoutOldFolder(@TempDir Path root)
    {
        File target = root.resolve("storage").resolve("GriefPrevention").toFile();
        assertFalse(DataStore.moveDataFolder(root.resolve("missing").toFile(), target));
        assertFalse(target.exists());
    }

    @Test
    void keepsExistingNewData(@TempDir Path root) throws Exception
    {
        Path old = root.resolve("old");
        Files.createDirectories(old);
        Files.writeString(old.resolve("config.yml"), "old");
        Path target = root.resolve("new");
        Files.createDirectories(target);
        Files.writeString(target.resolve("config.yml"), "new");

        assertFalse(DataStore.moveDataFolder(old.toFile(), target.toFile()));

        assertEquals("new", Files.readString(target.resolve("config.yml")));
        assertEquals("old", Files.readString(old.resolve("config.yml")));
    }

    @Test
    void replacesEmptyNewFolder(@TempDir Path root) throws Exception
    {
        Path old = root.resolve("old");
        Files.createDirectories(old);
        Files.writeString(old.resolve("config.yml"), "old");
        Path target = root.resolve("new");
        Files.createDirectories(target);

        assertTrue(DataStore.moveDataFolder(old.toFile(), target.toFile()));

        assertEquals("old", Files.readString(target.resolve("config.yml")));
        assertFalse(Files.exists(old));
    }
}
