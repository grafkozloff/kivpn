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

class XrayConfigGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    private final XrayConfigGenerator generator = new XrayConfigGenerator();

    private static JsonNode proxy(String config) throws Exception {
        return JSON.readTree(config).get("outbounds").get(0);
    }

    @Test
    void vlessRealityOutbound() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443"
                + "?type=tcp&security=reality&pbk=PUB_KEY&sid=abcd&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#R");
        JsonNode out = proxy(generator.generate(profile));

        assertEquals("vless", out.get("protocol").asText());
        JsonNode vnext = out.get("settings").get("vnext").get(0);
        assertEquals("example.com", vnext.get("address").asText());
        assertEquals(443, vnext.get("port").asInt());
        JsonNode user = vnext.get("users").get(0);
        assertEquals(UUID, user.get("id").asText());
        assertEquals("none", user.get("encryption").asText());
        assertEquals("xtls-rprx-vision", user.get("flow").asText());

        JsonNode stream = out.get("streamSettings");
        assertEquals("tcp", stream.get("network").asText());
        assertEquals("reality", stream.get("security").asText());
        JsonNode reality = stream.get("realitySettings");
        assertEquals("PUB_KEY", reality.get("publicKey").asText());
        assertEquals("abcd", reality.get("shortId").asText());
        assertEquals("www.microsoft.com", reality.get("serverName").asText());
        assertEquals("chrome", reality.get("fingerprint").asText());
    }

    @Test
    void vlessTlsWsOutbound() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443"
                + "?type=ws&security=tls&path=%2Fws&host=cdn.example.com&sni=cdn.example.com#WS");
        JsonNode stream = proxy(generator.generate(profile)).get("streamSettings");
        assertEquals("ws", stream.get("network").asText());
        assertEquals("tls", stream.get("security").asText());
        assertEquals("/ws", stream.get("wsSettings").get("path").asText());
        assertEquals("cdn.example.com", stream.get("wsSettings").get("headers").get("Host").asText());
        assertEquals("cdn.example.com", stream.get("tlsSettings").get("serverName").asText());
    }

    @Test
    void shadowsocksOutbound() throws Exception {
        String userinfo = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2022-blake3-aes-128-gcm:KEY".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Profile profile = new SsParser().parse("ss://" + userinfo + "@example.com:8388#SS");
        JsonNode out = proxy(generator.generate(profile));
        assertEquals("shadowsocks", out.get("protocol").asText());
        JsonNode server = out.get("settings").get("servers").get(0);
        assertEquals("example.com", server.get("address").asText());
        assertEquals(8388, server.get("port").asInt());
        assertEquals("2022-blake3-aes-128-gcm", server.get("method").asText());
        assertEquals("KEY", server.get("password").asText());
    }

    @Test
    void trojanOutbound() throws Exception {
        Profile profile = new TrojanParser().parse("trojan://secret@example.com:443?sni=example.com#T");
        JsonNode out = proxy(generator.generate(profile));
        assertEquals("trojan", out.get("protocol").asText());
        assertEquals("secret", out.get("settings").get("servers").get(0).get("password").asText());
        assertEquals("tls", out.get("streamSettings").get("security").asText());
        assertEquals("example.com", out.get("streamSettings").get("tlsSettings").get("serverName").asText());
    }

    @Test
    void hasLocalProxyInbounds() throws Exception {
        Profile profile = new VlessParser().parse("vless://" + UUID + "@example.com:443#R");
        JsonNode inbounds = JSON.readTree(generator.generate(profile)).get("inbounds");
        assertEquals("socks", inbounds.get(0).get("protocol").asText());
        assertEquals("127.0.0.1", inbounds.get(0).get("listen").asText());
        assertEquals("http", inbounds.get(1).get("protocol").asText());
    }

    @Test
    void unsupportedProtocolThrows() {
        Profile profile = new Profile(1L, null, "X", "wireguard", "example.com", 51820, "wireguard://x", "{}", false);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(profile));
    }

    @Test
    void moduleTypeIsXray() {
        assertEquals("xray", generator.moduleType());
        assertTrue(generator.generate(new Profile(1L, null, "X", "ss", "h", 1, "ss://x",
                "{\"method\":\"aes-256-gcm\",\"password\":\"p\"}", false)).contains("shadowsocks"));
    }
}
