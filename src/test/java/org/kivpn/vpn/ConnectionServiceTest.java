package org.kivpn.vpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SchemaInitializer;
import org.kivpn.db.SettingsRepository;
import org.kivpn.db.SqliteConnectionManager;
import org.kivpn.model.Profile;

class ConnectionServiceTest {

    @TempDir
    Path tempDir;

    private ProfileRepository profiles;
    private SettingsRepository settings;
    private FakeProcessManager processManager;
    private ConnectionService service;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("data/kivpn.db").toString();
        SqliteConnectionManager connections = new SqliteConnectionManager(dbPath);
        new SchemaInitializer(connections).init();
        profiles = new ProfileRepository(connections);
        settings = new SettingsRepository(connections);
        processManager = new FakeProcessManager();
        ConfigGeneratorDispatcher generators =
                new ConfigGeneratorDispatcher(List.of(new XrayConfigGenerator(), new SingBoxConfigGenerator()));
        service = new ConnectionService(profiles, settings, generators, processManager, dbPath);
    }

    private long addActiveProfile() throws Exception {
        long id = profiles.add(new Profile(null, null, "Server", "vless", "example.com", 443,
                "vless://x", "{\"uuid\":\"11111111-2222-3333-4444-555555555555\",\"security\":\"tls\"}", false));
        profiles.setActive(id);
        return id;
    }

    @Test
    void connectWithoutActiveProfileThrows() {
        ConnectionException e = assertThrows(ConnectionException.class, () -> service.connect());
        assertTrue(e.getMessage().contains("Нет активного профиля"));
    }

    @Test
    void connectWithoutModuleThrows() throws Exception {
        addActiveProfile();
        ConnectionException e = assertThrows(ConnectionException.class, () -> service.connect());
        assertTrue(e.getMessage().contains("модуль не настроен"));
    }

    @Test
    void connectStartsProcessAndWritesConfig() throws Exception {
        addActiveProfile();
        settings.set(ConnectionService.MODULE_PATH, "C:/xray/xray.exe");
        String message = service.connect();
        assertTrue(message.contains("Подключение запущено"));
        assertTrue(processManager.isRunning());
        assertEquals("C:/xray/xray.exe", processManager.lastCommand().get(0));
        assertTrue(processManager.lastCommand().contains("run"));
        assertTrue(Files.exists(tempDir.resolve("data/module-config.json")));
        assertTrue(service.status().contains("подключено"));
    }

    @Test
    void connectTwiceThrows() throws Exception {
        addActiveProfile();
        settings.set(ConnectionService.MODULE_PATH, "C:/xray/xray.exe");
        service.connect();
        ConnectionException e = assertThrows(ConnectionException.class, () -> service.connect());
        assertTrue(e.getMessage().contains("уже активно"));
    }

    @Test
    void disconnectStopsProcess() throws Exception {
        addActiveProfile();
        settings.set(ConnectionService.MODULE_PATH, "C:/xray/xray.exe");
        service.connect();
        String message = service.disconnect();
        assertEquals("Подключение остановлено.", message);
        assertFalse(processManager.isRunning());
    }

    @Test
    void disconnectWhenIdleReportsNotActive() {
        assertEquals("Подключение не активно.", service.disconnect());
    }

    @Test
    void setModuleInfersSingBoxType() throws Exception {
        service.setModule("C:/tools/sing-box.exe", null);
        assertEquals("sing-box", settings.get(ConnectionService.MODULE_TYPE).orElseThrow());
        assertTrue(service.moduleInfo().contains("sing-box"));
    }

    @Test
    void setModuleDefaultsToXray() throws Exception {
        service.setModule("C:/tools/xray.exe", null);
        assertEquals("xray", settings.get(ConnectionService.MODULE_TYPE).orElseThrow());
    }

    @Test
    void setModuleExplicitType() throws Exception {
        service.setModule("C:/tools/core.exe", "sing-box");
        assertEquals("sing-box", settings.get(ConnectionService.MODULE_TYPE).orElseThrow());
    }

    @Test
    void setModuleInvalidTypeThrows() {
        ConnectionException e = assertThrows(ConnectionException.class,
                () -> service.setModule("C:/tools/core.exe", "wireguard"));
        assertTrue(e.getMessage().contains("Неподдерживаемый тип модуля"));
    }

    @Test
    void onProfileSwitchStopsWhenDifferent() throws Exception {
        long id = addActiveProfile();
        settings.set(ConnectionService.MODULE_PATH, "C:/xray/xray.exe");
        service.connect();
        service.onProfileSwitch(id + 1);
        assertFalse(processManager.isRunning());
    }

    @Test
    void onProfileSwitchKeepsWhenSame() throws Exception {
        long id = addActiveProfile();
        settings.set(ConnectionService.MODULE_PATH, "C:/xray/xray.exe");
        service.connect();
        service.onProfileSwitch(id);
        assertTrue(processManager.isRunning());
    }
}
