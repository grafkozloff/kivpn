package org.kivpn.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kivpn.model.Source;

class SubscriptionSourceProviderTest {

    private static final String VLESS = "vless://11111111-2222-3333-4444-555555555555@example.com:443#A";
    private static final String TROJAN = "trojan://secret@example.org:443#B";

    private static Source source(String url) {
        return new Source(1L, "Sub", "subscription", url, true);
    }

    @Test
    void parseLinksFromPlainLines() {
        List<String> links = SubscriptionSourceProvider.parseLinks("# comment\n\n" + VLESS + "\n" + TROJAN + "\n");
        assertEquals(List.of(VLESS, TROJAN), links);
    }

    @Test
    void parseLinksFromBase64Blob() {
        String blob = Base64.getEncoder().encodeToString((VLESS + "\n" + TROJAN).getBytes(StandardCharsets.UTF_8));
        List<String> links = SubscriptionSourceProvider.parseLinks(blob);
        assertEquals(List.of(VLESS, TROJAN), links);
    }

    @Test
    void parseLinksIgnoresNonLinkLines() {
        List<String> links = SubscriptionSourceProvider.parseLinks("not a link\n" + VLESS + "\nplain text");
        assertEquals(List.of(VLESS), links);
    }

    @Test
    void fetchUsesFetcherAndParses() throws Exception {
        SubscriptionSourceProvider provider = new SubscriptionSourceProvider(url -> VLESS + "\n" + TROJAN);
        assertEquals(List.of(VLESS, TROJAN), provider.fetch(source("https://example.org/sub")));
    }

    @Test
    void fetchWrapsFetcherError() {
        SubscriptionSourceProvider provider = new SubscriptionSourceProvider(url -> {
            throw new IOException("network down");
        });
        IOException e = assertThrows(IOException.class, () -> provider.fetch(source("https://example.org/sub")));
        assertTrue(e.getMessage().contains("network down"));
    }

    @Test
    void typeIsSubscription() {
        assertEquals("subscription", new SubscriptionSourceProvider(url -> "").type());
    }
}
