package org.kivpn.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kivpn.model.Profile;

class VlessParserTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final VlessParser parser = new VlessParser();

    private static Map<String, String> params(Profile profile) throws Exception {
        return JSON.readValue(profile.params(), new TypeReference<Map<String, String>>() {
        });
    }

    @Test
    void parsesRealityLinkWithFragmentName() throws Exception {
        String link = "vless://" + UUID + "@example.com:443"
                + "?type=tcp&security=reality&pbk=ABC_def-123&sid=abcd1234"
                + "&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#%D0%A1%D0%B5%D1%80%D0%B2%D0%B5%D1%80";

        Profile profile = parser.parse(link);

        assertNull(profile.id());
        assertNull(profile.sourceId());
        assertEquals("Сервер", profile.name());
        assertEquals("vless", profile.protocol());
        assertEquals("example.com", profile.address());
        assertEquals(443, profile.port());
        assertEquals(link, profile.uri());
        assertFalse(profile.active());

        Map<String, String> params = params(profile);
        assertEquals(UUID, params.get("uuid"));
        assertEquals("tcp", params.get("type"));
        assertEquals("reality", params.get("security"));
        assertEquals("ABC_def-123", params.get("pbk"));
        assertEquals("abcd1234", params.get("sid"));
        assertEquals("www.microsoft.com", params.get("sni"));
        assertEquals("chrome", params.get("fp"));
        assertEquals("xtls-rprx-vision", params.get("flow"));
    }

    @Test
    void parsesWsTlsLinkAndDecodesQueryValues() throws Exception {
        String link = "vless://" + UUID + "@host.net:8443"
                + "?type=ws&security=tls&path=%2Fws&host=cdn.example.com&sni=cdn.example.com#WS%20Node";

        Profile profile = parser.parse(link);

        assertEquals("WS Node", profile.name());
        assertEquals("host.net", profile.address());
        assertEquals(8443, profile.port());

        Map<String, String> params = params(profile);
        assertEquals("ws", params.get("type"));
        assertEquals("tls", params.get("security"));
        assertEquals("/ws", params.get("path"));
        assertEquals("cdn.example.com", params.get("host"));
    }

    @Test
    void nameFallsBackToHostWhenFragmentMissing() throws Exception {
        Profile profile = parser.parse("vless://" + UUID + "@example.org:443?security=tls");
        assertEquals("example.org", profile.name());
    }

    @Test
    void nameFallsBackToHostWhenFragmentEmpty() throws Exception {
        Profile profile = parser.parse("vless://" + UUID + "@example.org:443#");
        assertEquals("example.org", profile.name());
    }

    @Test
    void withoutQueryParamsStoresOnlyUuid() throws Exception {
        Profile profile = parser.parse("vless://" + UUID + "@example.org:443");
        Map<String, String> params = params(profile);
        assertEquals(1, params.size());
        assertEquals(UUID, params.get("uuid"));
    }

    @Test
    void parsesIpv6Address() throws Exception {
        Profile profile = parser.parse("vless://" + UUID + "@[2001:db8::1]:443?security=tls#v6");
        assertEquals("2001:db8::1", profile.address());
        assertEquals(443, profile.port());
        assertEquals("v6", profile.name());
    }

    @Test
    void schemeIsCaseInsensitiveAndInputTrimmed() throws Exception {
        Profile profile = parser.parse("  VLESS://" + UUID + "@example.org:443  ");
        assertEquals("vless", profile.protocol());
        assertEquals("example.org", profile.address());
        assertTrue(profile.uri().startsWith("VLESS://"));
    }

    @Test
    void flagParamWithoutValueIsStoredEmpty() throws Exception {
        Profile profile = parser.parse("vless://" + UUID + "@example.org:443?allowInsecure=1&insecure");
        Map<String, String> params = params(profile);
        assertEquals("1", params.get("allowInsecure"));
        assertEquals("", params.get("insecure"));
    }

    @Test
    void rejectsWrongScheme() {
        ProfileParseException e = assertThrows(ProfileParseException.class,
                () -> parser.parse("ss://" + UUID + "@example.org:443"));
        assertTrue(e.getMessage().contains("vless://"));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(ProfileParseException.class, () -> parser.parse(null));
        assertThrows(ProfileParseException.class, () -> parser.parse("   "));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://"));
    }

    @Test
    void rejectsMissingUuid() {
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://example.org:443"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://@example.org:443"));
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@:443"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@"));
    }

    @Test
    void rejectsMissingOrInvalidPort() {
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@example.org"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@example.org:"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@example.org:abc"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@example.org:0"));
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@example.org:70000"));
    }

    @Test
    void rejectsMalformedIpv6() {
        assertThrows(ProfileParseException.class, () -> parser.parse("vless://" + UUID + "@[2001:db8::1:443"));
    }
}
