package org.kivpn.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileParserDispatcherTest {

    private final ProfileParserDispatcher dispatcher =
            new ProfileParserDispatcher(List.of(new VlessParser(), new SsParser(), new TrojanParser()));

    @Test
    void routesVless() throws Exception {
        assertEquals("vless", dispatcher.parse(
                "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls").protocol());
    }

    @Test
    void routesTrojan() throws Exception {
        assertEquals("trojan", dispatcher.parse("trojan://secret@example.com:443?sni=example.com").protocol());
    }

    @Test
    void routesSs() throws Exception {
        String userinfo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-256-gcm:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals("ss", dispatcher.parse("ss://" + userinfo + "@example.com:8388").protocol());
    }

    @Test
    void schemeIsCaseInsensitive() throws Exception {
        assertEquals("vless", dispatcher.parse(
                "VLESS://11111111-2222-3333-4444-555555555555@example.com:443").protocol());
    }

    @Test
    void unknownSchemeReportsError() {
        ProfileParseException e = assertThrows(ProfileParseException.class,
                () -> dispatcher.parse("hysteria2://secret@example.com:443"));
        assertTrue(e.getMessage().contains("Неподдерживаемый протокол: hysteria2"));
    }

    @Test
    void missingSchemeReportsError() {
        assertThrows(ProfileParseException.class, () -> dispatcher.parse("example.com:443"));
    }

    @Test
    void nullAndBlankReportError() {
        assertThrows(ProfileParseException.class, () -> dispatcher.parse(null));
        assertThrows(ProfileParseException.class, () -> dispatcher.parse("   "));
    }
}
