package org.kivpn.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.kivpn.model.Source;

class SqliteLayerTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionManager connections;
    private ProfileRepository profiles;
    private SourceRepository sources;
    private CheckRepository checks;

    @BeforeEach
    void setUp() {
        connections = new SqliteConnectionManager(tempDir.resolve("data/kivpn.db").toString());
        new SchemaInitializer(connections).init();
        profiles = new ProfileRepository(connections);
        sources = new SourceRepository(connections);
        checks = new CheckRepository(connections);
    }

    private static int pragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA " + name)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String pragmaText(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA " + name)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Profile sampleProfile(Long sourceId, String name) {
        return new Profile(null, sourceId, name, "vless", "example.org", 443,
                "vless://uuid@example.org:443?security=reality", null, false);
    }

    @Test
    void initCreatesDatabaseWithRequiredPragmas() throws Exception {
        assertTrue(Files.exists(connections.dbPath()));
        try (Connection connection = connections.open()) {
            assertEquals("wal", pragmaText(connection, "journal_mode"));
            assertEquals(1, pragma(connection, "synchronous"));
            assertEquals(1, pragma(connection, "foreign_keys"));
            assertEquals(5000, pragma(connection, "busy_timeout"));
            assertEquals(2, pragma(connection, "user_version"));
        }
    }

    @Test
    void repeatedInitPreservesDataAndVersion() throws Exception {
        long sourceId = sources.add(new Source(null, "Подписка", "subscription", "https://example.org/sub", true));
        long profileId = profiles.add(sampleProfile(sourceId, "Основной"));
        checks.add(new CheckResult(null, profileId, "ok", 42, Instant.now().toString(), null));

        new SchemaInitializer(new SqliteConnectionManager(connections.dbPath().toString())).init();

        assertEquals(1, sources.findAll().size());
        assertEquals(1, profiles.findAll().size());
        assertEquals(42, checks.findLatest(profileId).orElseThrow().latencyMs());
        try (Connection connection = connections.open()) {
            assertEquals(2, pragma(connection, "user_version"));
        }
    }

    @Test
    void profileCrudAndActiveSelection() throws Exception {
        long first = profiles.add(sampleProfile(null, "Первый"));
        long second = profiles.add(sampleProfile(null, "Второй"));

        profiles.setActive(first);
        assertEquals(first, profiles.findActive().orElseThrow().id());
        profiles.setActive(second);
        assertEquals(second, profiles.findActive().orElseThrow().id());
        assertFalse(profiles.findById(first).orElseThrow().active());

        Profile updated = new Profile(first, null, "Переименованный", "ss", "example.net", 8443,
                "ss://key@example.net:8443", "{\"network\":\"tcp\"}", false);
        profiles.update(updated);
        Profile stored = profiles.findById(first).orElseThrow();
        assertEquals("Переименованный", stored.name());
        assertEquals("ss", stored.protocol());
        assertEquals("{\"network\":\"tcp\"}", stored.params());

        profiles.delete(first);
        assertTrue(profiles.findById(first).isEmpty());
        assertEquals(1, profiles.findAll().size());
    }

    @Test
    void sourceCrudAndDeleteKeepsProfiles() throws Exception {
        long sourceId = sources.add(new Source(null, "Файл", "file", "C:/configs.txt", true));
        long profileId = profiles.add(sampleProfile(sourceId, "Из файла"));
        assertEquals(List.of(profileId), profiles.findBySource(sourceId).stream().map(Profile::id).toList());

        sources.setEnabled(sourceId, false);
        assertFalse(sources.findById(sourceId).orElseThrow().enabled());

        sources.delete(sourceId);
        assertTrue(sources.findById(sourceId).isEmpty());
        assertNull(profiles.findById(profileId).orElseThrow().sourceId());
    }

    @Test
    void checkResultsPersistAndLatestWins() throws Exception {
        long profileId = profiles.add(sampleProfile(null, "Сервер"));
        checks.add(new CheckResult(null, profileId, "fail", null, Instant.now().toString(), "timeout"));
        long lastId = checks.add(new CheckResult(null, profileId, "ok", 17, Instant.now().toString(), null));

        CheckResult latest = checks.findLatest(profileId).orElseThrow();
        assertEquals(lastId, latest.id());
        assertEquals("ok", latest.status());
        assertEquals(17, latest.latencyMs());
        assertNull(latest.error());

        Map<Long, CheckResult> perProfile = checks.findLatestPerProfile();
        assertEquals(1, perProfile.size());
        assertEquals(lastId, perProfile.get(profileId).id());
    }

    @Test
    void foreignKeysRejectOrphanCheck() throws Exception {
        assertThrows(SQLException.class,
                () -> checks.add(new CheckResult(null, 999, "ok", 1, Instant.now().toString(), null)));
    }

    @Test
    void deletingProfileRemovesItsChecks() throws Exception {
        long profileId = profiles.add(sampleProfile(null, "Сервер"));
        checks.add(new CheckResult(null, profileId, "ok", 5, Instant.now().toString(), null));
        profiles.delete(profileId);
        assertTrue(checks.findLatest(profileId).isEmpty());
        assertTrue(checks.findLatestPerProfile().isEmpty());
    }
}
