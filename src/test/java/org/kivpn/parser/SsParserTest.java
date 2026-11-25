package org.kivpn.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kivpn.model.Profile;

class SsParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SsParser parser = new SsParser();

    private static Map<String, String> params(Profile profile) throws Exception {
        return JSON.readValue(profile.params(), new TypeReference<Map<String, String>>() {
        });
    }

    private static String sip002(String method, String password, String hostPort, String fragment) {
        String userinfo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((method + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "ss://" + userinfo + "@" + hostPort + (fragment == null ? "" : "#" + fragment);
    }

    @Test
    void parsesSip002Link() throws Exception {
        String link = sip002("aes-256-gcm", "secret_password", "example.com:8388", "SS%20Node");
        Profile profile = parser.parse(link);

        assertNull(profile.id());
        assertEquals("ss", profile.protocol());
        assertEquals("SS Node", profile.name());
        assertEquals("example.com", profile.address());
        assertEquals(8388, profile.port());
        assertEquals(link, profile.uri());
        assertFalse(profile.active());

        Map<String, String> params = params(profile);
        assertEquals("aes-256-gcm", params.get("method"));
        assertEquals("secret_password", params.get("password"));
    }

    @Test
    void parsesShadowsocks2022WithBase64Password() throws Exception {
        String key = "YWJjZGVmZ2hpamtsbW5vcA==";
        String link = sip002("2022-blake3-aes-128-gcm", key, "example.com:8388", "SS2022");
        Profile profile = parser.parse(link);

        Map<String, String> params = params(profile);
        assertEquals("2022-blake3-aes-128-gcm", params.get("method"));
        assertEquals(key, params.get("password"));
    }

    @Test
    void parsesPluginQuery() throws Exception {
        String link = sip002("aes-256-gcm", "pass", "example.com:8388", null)
                + "?plugin=obfs-local;obfs=http;obfs-host=cdn.example.com";
        Profile profile = parser.parse(link);
        assertEquals("obfs-local;obfs=http;obfs-host=cdn.example.com", params(profile).get("plugin"));
    }

    @Test
    void parsesLegacyFullyBase64Link() throws Exception {
        String body = Base64.getEncoder()
                .encodeToString("aes-256-gcm:pass@example.com:8388".getBytes(StandardCharsets.UTF_8));
        Profile profile = parser.parse("ss://" + body + "#Legacy");

        assertEquals("Legacy", profile.name());
        assertEquals("example.com", profile.address());
        assertEquals(8388, profile.port());
        Map<String, String> params = params(profile);
        assertEquals("aes-256-gcm", params.get("method"));
        assertEquals("pass", params.get("password"));
    }

    @Test
    void nameFallsBackToHost() throws Exception {
        Profile profile = parser.parse(sip002("aes-256-gcm", "pass", "example.org:8388", null));
        assertEquals("example.org", profile.name());
    }

    @Test
    void parsesIpv6Address() throws Exception {
        Profile profile = parser.parse(sip002("aes-256-gcm", "pass", "[2001:db8::1]:8388", "v6"));
        assertEquals("2001:db8::1", profile.address());
        assertEquals(8388, profile.port());
    }

    @Test
    void rejectsWrongScheme() {
        ProfileParseException e = assertThrows(ProfileParseException.class,
                () -> parser.parse("vless://uuid@example.com:443"));
        assertTrue(e.getMessage().contains("ss://"));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(ProfileParseException.class, () -> parser.parse(null));
        assertThrows(ProfileParseException.class, () -> parser.parse("ss://"));
    }

    @Test
    void rejectsInvalidBase64() {
        assertThrows(ProfileParseException.class, () -> parser.parse("ss://!!!not-base64!!!@example.com:8388"));
    }

    @Test
    void rejectsMissingMethodOrPassword() {
        String noColon = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("justpassword".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProfileParseException.class, () -> parser.parse("ss://" + noColon + "@example.com:8388"));

        String emptyPassword = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-256-gcm:".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProfileParseException.class, () -> parser.parse("ss://" + emptyPassword + "@example.com:8388"));
    }

    @Test
    void rejectsMissingPort() {
        assertThrows(ProfileParseException.class,
                () -> parser.parse(sip002("aes-256-gcm", "pass", "example.com", null)));
    }
}
