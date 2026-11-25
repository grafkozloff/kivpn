package org.kivpn.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kivpn.model.Profile;

class TrojanParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TrojanParser parser = new TrojanParser();

    private static Map<String, String> params(Profile profile) throws Exception {
        return JSON.readValue(profile.params(), new TypeReference<Map<String, String>>() {
        });
    }

    @Test
    void parsesTlsLink() throws Exception {
        String link = "trojan://secret@example.com:443?sni=example.com&type=tcp&security=tls&fp=chrome#Trojan%20Node";
        Profile profile = parser.parse(link);

        assertEquals("trojan", profile.protocol());
        assertEquals("Trojan Node", profile.name());
        assertEquals("example.com", profile.address());
        assertEquals(443, profile.port());
        assertEquals(link, profile.uri());
        assertFalse(profile.active());

        Map<String, String> params = params(profile);
        assertEquals("secret", params.get("password"));
        assertEquals("example.com", params.get("sni"));
        assertEquals("tcp", params.get("type"));
        assertEquals("tls", params.get("security"));
        assertEquals("chrome", params.get("fp"));
    }

    @Test
    void decodesUrlEncodedPassword() throws Exception {
        Profile profile = parser.parse("trojan://p%40ss%3Aword@example.com:443#Node");
        assertEquals("p@ss:word", params(profile).get("password"));
    }

    @Test
    void parsesWsTransport() throws Exception {
        Profile profile = parser.parse(
                "trojan://secret@example.com:443?type=ws&path=%2Fws&host=cdn.example.com&sni=cdn.example.com#WS");
        Map<String, String> params = params(profile);
        assertEquals("ws", params.get("type"));
        assertEquals("/ws", params.get("path"));
        assertEquals("cdn.example.com", params.get("host"));
    }

    @Test
    void nameFallsBackToHost() throws Exception {
        Profile profile = parser.parse("trojan://secret@example.org:443");
        assertEquals("example.org", profile.name());
    }

    @Test
    void parsesIpv6Address() throws Exception {
        Profile profile = parser.parse("trojan://secret@[2001:db8::1]:443#v6");
        assertEquals("2001:db8::1", profile.address());
        assertEquals(443, profile.port());
    }

    @Test
    void rejectsWrongScheme() {
        ProfileParseException e = assertThrows(ProfileParseException.class,
                () -> parser.parse("ss://abc@example.com:8388"));
        assertTrue(e.getMessage().contains("trojan://"));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(ProfileParseException.class, () -> parser.parse(null));
        assertThrows(ProfileParseException.class, () -> parser.parse("trojan://"));
    }

    @Test
    void rejectsMissingOrEmptyPassword() {
        assertThrows(ProfileParseException.class, () -> parser.parse("trojan://example.com:443"));
        assertThrows(ProfileParseException.class, () -> parser.parse("trojan://@example.com:443"));
    }

    @Test
    void rejectsMissingPort() {
        assertThrows(ProfileParseException.class, () -> parser.parse("trojan://secret@example.com"));
    }
}
