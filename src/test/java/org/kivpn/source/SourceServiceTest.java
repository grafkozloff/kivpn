package org.kivpn.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SchemaInitializer;
import org.kivpn.db.SourceRepository;
import org.kivpn.db.SqliteConnectionManager;
import org.kivpn.model.Profile;
import org.kivpn.model.Source;
import org.kivpn.parser.ProfileParserDispatcher;
import org.kivpn.parser.SsParser;
import org.kivpn.parser.TrojanParser;
import org.kivpn.parser.VlessParser;

class SourceServiceTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final String VLESS = "vless://" + UUID + "@example.com:443?security=tls#A";
    private static final String TROJAN = "trojan://secret@example.org:443?sni=example.org#B";

    @TempDir
    java.nio.file.Path tempDir;

    private SourceRepository sourceRepository;
    private ProfileRepository profileRepository;
    private SourceService service;
    private String subscriptionContent;
    private boolean failFetch;

    @BeforeEach
    void setUp() {
        SqliteConnectionManager connections =
                new SqliteConnectionManager(tempDir.resolve("data/kivpn.db").toString());
        new SchemaInitializer(connections).init();
        sourceRepository = new SourceRepository(connections);
        profileRepository = new ProfileRepository(connections);
        ProfileParserDispatcher parsers =
                new ProfileParserDispatcher(List.of(new VlessParser(), new SsParser(), new TrojanParser()));
        SubscriptionSourceProvider subscription = new SubscriptionSourceProvider(url -> {
            if (failFetch) {
                throw new IOException("network down");
            }
            return subscriptionContent;
        });
        SourceProviderDispatcher providers =
                new SourceProviderDispatcher(List.of(subscription, new FileSourceProvider()));
        service = new SourceService(sourceRepository, profileRepository, providers, parsers);
        subscriptionContent = VLESS + "\n" + TROJAN;
    }

    @Test
    void addSourceInvalidTypeThrows() {
        SourceException e = assertThrows(SourceException.class,
                () -> service.addSource("X", "vpngate", "https://example.org"));
        assertTrue(e.getMessage().contains("Неподдерживаемый тип источника"));
    }

    @Test
    void addSourceAndList() throws Exception {
        Source source = service.addSource("My Sub", "subscription", "https://example.org/sub");
        assertEquals("subscription", source.type());
        assertTrue(source.enabled());
        assertEquals(1, service.listSources().size());
    }

    @Test
    void updateImportsProfilesWithSourceId() throws Exception {
        Source source = service.addSource("Sub", "subscription", "https://example.org/sub");
        SourceService.UpdateSummary summary = service.update(null);
        assertEquals(2, summary.added());
        assertEquals(0, summary.skipped());
        List<Profile> profiles = profileRepository.findAll();
        assertEquals(2, profiles.size());
        assertTrue(profiles.stream().allMatch(p -> source.id().equals(p.sourceId())));
    }

    @Test
    void updateDeduplicatesByUri() throws Exception {
        service.addSource("Sub", "subscription", "https://example.org/sub");
        service.update(null);
        SourceService.UpdateSummary second = service.update(null);
        assertEquals(0, second.added());
        assertEquals(2, second.skipped());
        assertEquals(2, profileRepository.findAll().size());
    }

    @Test
    void updateKeepsManualProfiles() throws Exception {
        profileRepository.add(new Profile(null, null, "Manual", "vless", "manual.example", 443,
                "vless://" + UUID + "@manual.example:443#M", "{}", false));
        service.addSource("Sub", "subscription", "https://example.org/sub");
        service.update(null);
        List<Profile> profiles = profileRepository.findAll();
        assertEquals(3, profiles.size());
        assertTrue(profiles.stream().anyMatch(p -> p.name().equals("Manual") && p.sourceId() == null));
    }

    @Test
    void updateSkipsDisabledSources() throws Exception {
        Source source = service.addSource("Sub", "subscription", "https://example.org/sub");
        service.setEnabled(source.id(), false);
        SourceService.UpdateSummary summary = service.update(null);
        assertEquals(0, summary.added());
        assertTrue(profileRepository.findAll().isEmpty());
    }

    @Test
    void updateUnknownIdThrows() {
        SourceException e = assertThrows(SourceException.class, () -> service.update(999L));
        assertTrue(e.getMessage().contains("не найден"));
    }

    @Test
    void updateFetchErrorIsCollectedNotThrown() throws Exception {
        service.addSource("Sub", "subscription", "https://example.org/sub");
        failFetch = true;
        SourceService.UpdateSummary summary = service.update(null);
        assertEquals(0, summary.added());
        assertEquals(1, summary.errors().size());
        assertTrue(summary.errors().get(0).contains("network down"));
    }

    @Test
    void removeSourceKeepsProfiles() throws Exception {
        Source source = service.addSource("Sub", "subscription", "https://example.org/sub");
        service.update(null);
        service.removeSource(source.id());
        assertTrue(service.listSources().isEmpty());
        List<Profile> profiles = profileRepository.findAll();
        assertEquals(2, profiles.size());
        assertTrue(profiles.stream().allMatch(p -> p.sourceId() == null));
    }

    @Test
    void setEnabledUnknownIdThrows() {
        assertThrows(SourceException.class, () -> service.setEnabled(999L, true));
    }
}
