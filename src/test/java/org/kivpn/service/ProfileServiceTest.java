package org.kivpn.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.db.CheckRepository;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SchemaInitializer;
import org.kivpn.db.SqliteConnectionManager;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.kivpn.parser.ProfileParseException;
import org.kivpn.parser.ProfileParserDispatcher;
import org.kivpn.parser.SsParser;
import org.kivpn.parser.TrojanParser;
import org.kivpn.parser.VlessParser;

class ProfileServiceTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final String LINK = "vless://" + UUID + "@example.com:443?security=tls#Server";

    @TempDir
    Path tempDir;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        SqliteConnectionManager connections =
                new SqliteConnectionManager(tempDir.resolve("data/kivpn.db").toString());
        new SchemaInitializer(connections).init();
        service = new ProfileService(dispatcher(), new ProfileRepository(connections),
                new ServerChecker(probe(), 3000), new CheckRepository(connections), new ServerSelector());
    }

    private static ProfileParserDispatcher dispatcher() {
        return new ProfileParserDispatcher(List.of(new VlessParser(), new SsParser(), new TrojanParser()));
    }

    private static ServerChecker.Probe probe() {
        return (host, port, timeoutMs) -> {
            if (host.startsWith("fail")) {
                throw new IOException("connection refused");
            }
        };
    }

    @Test
    void addPersistsProfileAndAssignsId() throws Exception {
        Profile profile = service.add(LINK);
        assertNotNull(profile.id());
        assertEquals("Server", profile.name());
        assertEquals("vless", profile.protocol());
        assertEquals(LINK, profile.uri());

        List<Profile> stored = service.list();
        assertEquals(1, stored.size());
        assertEquals(profile.id(), stored.get(0).id());
    }

    @Test
    void addInvalidLinkThrowsAndStoresNothing() throws Exception {
        assertThrows(ProfileParseException.class, () -> service.add("vless://broken"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void importCountsSuccessesAndCollectsLineErrors() throws Exception {
        Path file = tempDir.resolve("profiles.txt");
        Files.writeString(file, """
                # комментарий
                %s
                vless://broken

                vless://%s@example.org:8443?security=tls#Second
                """.formatted(LINK, UUID));

        ProfileService.ImportSummary summary = service.importFile(file.toString());
        assertEquals(2, summary.imported());
        assertEquals(1, summary.errors().size());
        assertTrue(summary.errors().get(0).contains("Строка 3"));
        assertEquals(2, service.list().size());
    }

    @Test
    void importEmptyFileImportsNothing() throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "# только комментарий\n\n");
        ProfileService.ImportSummary summary = service.importFile(file.toString());
        assertEquals(0, summary.imported());
        assertTrue(summary.errors().isEmpty());
        assertTrue(service.list().isEmpty());
    }

    @Test
    void listPreservesInsertionOrder() throws Exception {
        service.add(LINK);
        service.add("vless://" + UUID + "@example.org:8443?security=tls#Second");
        List<Profile> all = service.list();
        assertEquals(2, all.size());
        assertEquals("Server", all.get(0).name());
        assertEquals("Second", all.get(1).name());
        assertTrue(all.get(0).id() < all.get(1).id());
    }

    @Test
    void importAcceptsAllSupportedProtocols() throws Exception {
        String ssUserinfo = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-256-gcm:pass".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path file = tempDir.resolve("mixed.txt");
        Files.writeString(file, LINK + "\n"
                + "ss://" + ssUserinfo + "@example.com:8388#SS\n"
                + "trojan://secret@example.org:443?sni=example.org#Trojan\n");

        ProfileService.ImportSummary summary = service.importFile(file.toString());
        assertEquals(3, summary.imported());
        assertTrue(summary.errors().isEmpty());

        List<Profile> all = service.list();
        assertEquals("vless", all.get(0).protocol());
        assertEquals("ss", all.get(1).protocol());
        assertEquals("trojan", all.get(2).protocol());
    }

    @Test
    void checkPersistsOkResultWithLatency() throws Exception {
        Profile profile = service.add("vless://" + UUID + "@ok.example.com:443#Ok");
        CheckResult result = service.check(profile);
        assertEquals("ok", result.status());
        assertNotNull(result.latencyMs());
        assertNotNull(result.id());
        assertEquals("ok", service.latestChecks().get(profile.id()).status());
    }

    @Test
    void checkPersistsFailResultWithError() throws Exception {
        Profile profile = service.add("vless://" + UUID + "@fail.example.com:443#Fail");
        CheckResult result = service.check(profile);
        assertEquals("fail", result.status());
        assertNotNull(result.error());
        assertEquals("fail", service.latestChecks().get(profile.id()).status());
    }

    @Test
    void useSetsActiveProfile() throws Exception {
        Profile first = service.add(LINK);
        Profile second = service.add("vless://" + UUID + "@example.org:8443#Second");
        service.use(second.id());
        List<Profile> all = service.list();
        assertFalse(all.get(0).active());
        assertTrue(all.get(1).active());
        assertEquals(second.id(), all.get(1).id());
        assertNotNull(first.id());
    }

    @Test
    void useAutoSelectsWorkingProfileAndSetsActive() throws Exception {
        Profile ok = service.add("vless://" + UUID + "@ok.example.com:443#Ok");
        Profile fail = service.add("vless://" + UUID + "@fail.example.com:443#Fail");
        service.check(ok);
        service.check(fail);

        Profile chosen = service.useAuto().orElseThrow();
        assertEquals(ok.id(), chosen.id());
        assertTrue(service.list().stream().filter(Profile::active).findFirst().orElseThrow().id() == ok.id());
    }

    @Test
    void useAutoWithoutWorkingProfilesReturnsEmpty() throws Exception {
        Profile fail = service.add("vless://" + UUID + "@fail.example.com:443#Fail");
        service.check(fail);
        assertTrue(service.useAuto().isEmpty());
    }

    @Test
    void exportToFileWritesUri() throws Exception {
        Profile profile = service.add(LINK);
        Path file = tempDir.resolve("exported.txt");
        service.exportToFile(profile, file.toString());
        assertEquals(LINK, Files.readString(file).trim());
    }

    @Test
    void exportAllToFileWritesEveryUri() throws Exception {
        service.add(LINK);
        service.add("vless://" + UUID + "@example.org:8443#Second");
        Path file = tempDir.resolve("all.txt");
        int count = service.exportAllToFile(file.toString());
        assertEquals(2, count);
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertEquals(LINK, lines.get(0));
    }
}
