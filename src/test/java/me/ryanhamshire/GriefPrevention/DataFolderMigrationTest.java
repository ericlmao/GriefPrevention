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
    void settingsStayInPluginsAndDataGoesToStorage()
    {
        String plugins = "plugins" + File.separator + "GriefPreventionData";
        String storage = "storage" + File.separator + "GriefPrevention";
        assertEquals(plugins, DataStore.dataLayerFolderPath);
        assertEquals(storage, DataStore.storageFolderPath);
        assertTrue(DataStore.configFilePath.startsWith(plugins));
        assertTrue(DataStore.messagesFilePath.startsWith(plugins));
        assertTrue(DataStore.bannedWordsFilePath.startsWith(plugins));
        assertTrue(DataStore.softMuteFilePath.startsWith(plugins));
        assertEquals(storage + File.separator + "PlayerData", DataStore.playerDataFolderPath);
    }

    @Test
    void movesDataFoldersOutOfPlugins(@TempDir Path root) throws Exception
    {
        Path settings = root.resolve("plugins").resolve("GriefPreventionData");
        Path storage = root.resolve("storage").resolve("GriefPrevention");
        Files.createDirectories(settings.resolve("ClaimData"));
        Files.createDirectories(settings.resolve("PlayerData"));
        Files.createDirectories(settings.resolve("Logs"));
        Files.createDirectories(settings.resolve("ClaimData1"));
        Files.writeString(settings.resolve("ClaimData").resolve("1.yml"), "claim");
        Files.writeString(settings.resolve("PlayerData").resolve("player"), "player");
        Files.writeString(settings.resolve("Logs").resolve("2026_09_13.log"), "log");
        Files.writeString(settings.resolve("config.yml"), "config");
        Files.writeString(settings.resolve("messages.yml"), "messages");

        DataStore.migrateStorage(settings.toFile(), storage.toFile());

        assertEquals("claim", Files.readString(storage.resolve("ClaimData").resolve("1.yml")));
        assertEquals("player", Files.readString(storage.resolve("PlayerData").resolve("player")));
        assertEquals("log", Files.readString(storage.resolve("Logs").resolve("2026_09_13.log")));
        assertTrue(Files.isDirectory(storage.resolve("ClaimData1")));
        assertFalse(Files.exists(settings.resolve("ClaimData")));
        assertFalse(Files.exists(settings.resolve("PlayerData")));
        assertFalse(Files.exists(settings.resolve("Logs")));
        assertEquals("config", Files.readString(settings.resolve("config.yml")));
        assertEquals("messages", Files.readString(settings.resolve("messages.yml")));
        assertFalse(Files.exists(storage.resolve("config.yml")));
    }

    @Test
    void movesSettingsBackFromEarlierStorageBuild(@TempDir Path root) throws Exception
    {
        Path settings = root.resolve("plugins").resolve("GriefPreventionData");
        Path storage = root.resolve("storage").resolve("GriefPrevention");
        Files.createDirectories(storage.resolve("ClaimData"));
        Files.writeString(storage.resolve("ClaimData").resolve("1.yml"), "claim");
        Files.writeString(storage.resolve("config.yml"), "config");
        Files.writeString(storage.resolve("messages.yml"), "messages");
        Files.writeString(storage.resolve("bannedWords.txt"), "words");
        Files.writeString(storage.resolve("_schemaVersion"), "3");

        DataStore.migrateStorage(settings.toFile(), storage.toFile());

        assertEquals("config", Files.readString(settings.resolve("config.yml")));
        assertEquals("messages", Files.readString(settings.resolve("messages.yml")));
        assertEquals("words", Files.readString(settings.resolve("bannedWords.txt")));
        assertEquals("3", Files.readString(settings.resolve("_schemaVersion")));
        assertFalse(Files.exists(storage.resolve("config.yml")));
        assertEquals("claim", Files.readString(storage.resolve("ClaimData").resolve("1.yml")));
        assertFalse(Files.exists(settings.resolve("ClaimData")));
    }

    @Test
    void neverOverwritesExistingFiles(@TempDir Path root) throws Exception
    {
        Path settings = root.resolve("plugins").resolve("GriefPreventionData");
        Path storage = root.resolve("storage").resolve("GriefPrevention");
        Files.createDirectories(settings.resolve("ClaimData"));
        Files.createDirectories(storage.resolve("ClaimData"));
        Files.writeString(settings.resolve("ClaimData").resolve("1.yml"), "old claim");
        Files.writeString(storage.resolve("ClaimData").resolve("1.yml"), "new claim");
        Files.writeString(settings.resolve("config.yml"), "plugins config");
        Files.writeString(storage.resolve("config.yml"), "storage config");

        DataStore.migrateStorage(settings.toFile(), storage.toFile());

        assertEquals("new claim", Files.readString(storage.resolve("ClaimData").resolve("1.yml")));
        assertEquals("old claim", Files.readString(settings.resolve("ClaimData").resolve("1.yml")));
        assertEquals("plugins config", Files.readString(settings.resolve("config.yml")));
        assertEquals("storage config", Files.readString(storage.resolve("config.yml")));
    }

    @Test
    void doesNothingWhenFoldersMissing(@TempDir Path root)
    {
        File settings = root.resolve("plugins").resolve("GriefPreventionData").toFile();
        File storage = root.resolve("storage").resolve("GriefPrevention").toFile();

        DataStore.migrateStorage(settings, storage);

        assertFalse(settings.exists());
        assertFalse(storage.exists());
    }

    @Test
    void replacesEmptyTargetFolder(@TempDir Path root) throws Exception
    {
        Path old = root.resolve("old");
        Files.createDirectories(old);
        Files.writeString(old.resolve("1.yml"), "claim");
        Path target = root.resolve("new");
        Files.createDirectories(target);

        assertTrue(DataStore.moveDataPath(old.toFile(), target.toFile()));

        assertEquals("claim", Files.readString(target.resolve("1.yml")));
        assertFalse(Files.exists(old));
    }
}
