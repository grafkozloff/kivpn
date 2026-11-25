package org.kivpn.vpn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kivpn.model.Profile;
import org.kivpn.parser.SsParser;
import org.kivpn.parser.TrojanParser;
import org.kivpn.parser.VlessParser;

class SingBoxConfigGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    private final SingBoxConfigGenerator generator = new SingBoxConfigGenerator();

    private static JsonNode proxy(String config) throws Exception {
        return JSON.readTree(config).get("outbounds").get(0);
    }

    @Test
    void vlessRealityOutbound() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443"
                + "?type=tcp&security=reality&pbk=PUB_KEY&sid=abcd&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#R");
        JsonNode out = proxy(generator.generate(profile));

        assertEquals("vless", out.get("type").asText());
        assertEquals("example.com", out.get("server").asText());
        assertEquals(443, out.get("server_port").asInt());
        assertEquals(UUID, out.get("uuid").asText());
        assertEquals("xtls-rprx-vision", out.get("flow").asText());

        JsonNode tls = out.get("tls");
        assertTrue(tls.get("enabled").asBoolean());
        assertEquals("www.microsoft.com", tls.get("server_name").asText());
        assertEquals("chrome", tls.get("utls").get("fingerprint").asText());
        assertTrue(tls.get("reality").get("enabled").asBoolean());
        assertEquals("PUB_KEY", tls.get("reality").get("public_key").asText());
        assertEquals("abcd", tls.get("reality").get("short_id").asText());
    }

    @Test
    void vlessWsTransport() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443"
                + "?type=ws&security=tls&path=%2Fws&host=cdn.example.com&sni=cdn.example.com#WS");
        JsonNode out = proxy(generator.generate(profile));
        assertEquals("ws", out.get("transport").get("type").asText());
        assertEquals("/ws", out.get("transport").get("path").asText());
        assertEquals("cdn.example.com", out.get("transport").get("headers").get("Host").asText());
    }

    @Test
    void shadowsocksOutbound() throws Exception {
        String userinfo = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2022-blake3-aes-128-gcm:KEY".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Profile profile = new SsParser().parse("ss://" + userinfo + "@example.com:8388#SS");
        JsonNode out = proxy(generator.generate(profile));
        assertEquals("shadowsocks", out.get("type").asText());
        assertEquals("2022-blake3-aes-128-gcm", out.get("method").asText());
        assertEquals("KEY", out.get("password").asText());
        assertEquals(8388, out.get("server_port").asInt());
    }

    @Test
    void trojanOutbound() throws Exception {
        Profile profile = new TrojanParser().parse("trojan://secret@example.com:443?sni=example.com#T");
        JsonNode out = proxy(generator.generate(profile));
        assertEquals("trojan", out.get("type").asText());
        assertEquals("secret", out.get("password").asText());
        assertTrue(out.get("tls").get("enabled").asBoolean());
        assertEquals("example.com", out.get("tls").get("server_name").asText());
    }

    @Test
    void hasMixedInboundAndRouteFinal() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443#R");
        JsonNode root = JSON.readTree(generator.generate(profile));
        assertEquals("mixed", root.get("inbounds").get(0).get("type").asText());
        assertEquals(2080, root.get("inbounds").get(0).get("listen_port").asInt());
        assertEquals("proxy", root.get("route").get("final").asText());
    }

    @Test
    void unsupportedProtocolThrows() {
        Profile profile = new Profile(1L, null, "X", "wireguard", "example.com", 51820, "wireguard://x", "{}", false);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(profile));
    }

    @Test
    void moduleTypeIsSingBox() {
        assertEquals("sing-box", generator.moduleType());
    }
}
