package org.kivpn.vpn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public final class XrayConfigGenerator implements ConfigGenerator {

    @Override
    public String moduleType() {
        return "xray";
    }

    @Override
    public String generate(Profile profile) {
        JsonNode params = ConfigSupport.params(profile);
        ObjectNode root = ConfigSupport.JSON.createObjectNode();
        root.putObject("log").put("loglevel", "warning");

        ArrayNode inbounds = root.putArray("inbounds");
        ObjectNode socks = inbounds.addObject();
        socks.put("tag", "socks");
        socks.put("listen", "127.0.0.1");
        socks.put("port", 10808);
        socks.put("protocol", "socks");
        socks.putObject("settings").put("udp", true);
        ObjectNode http = inbounds.addObject();
        http.put("tag", "http");
        http.put("listen", "127.0.0.1");
        http.put("port", 10809);
        http.put("protocol", "http");

        ArrayNode outbounds = root.putArray("outbounds");
        outbounds.add(proxyOutbound(profile, params));
        outbounds.addObject().put("tag", "direct").put("protocol", "freedom");
        outbounds.addObject().put("tag", "block").put("protocol", "blackhole");

        ObjectNode routing = root.putObject("routing");
        routing.put("domainStrategy", "IPIfNonMatch");
        ObjectNode privateRule = routing.putArray("rules").addObject();
        privateRule.put("type", "field");
        privateRule.put("outboundTag", "direct");
        privateRule.putArray("ip").add("geoip:private");

        ObjectNode dns = root.putObject("dns");
        ArrayNode servers = dns.putArray("servers");
        servers.add("https://1.1.1.1/dns-query");
        servers.add("8.8.8.8");

        return root.toPrettyString();
    }

    private static ObjectNode proxyOutbound(Profile profile, JsonNode params) {
        ObjectNode out = ConfigSupport.JSON.createObjectNode();
        out.put("tag", "proxy");
        switch (profile.protocol()) {
            case "vless" -> vless(out, profile, params);
            case "ss" -> shadowsocks(out, profile, params);
            case "trojan" -> trojan(out, profile, params);
            default -> throw new IllegalArgumentException("Неподдерживаемый протокол: " + profile.protocol());
        }
        return out;
    }

    private static void vless(ObjectNode out, Profile profile, JsonNode params) {
        out.put("protocol", "vless");
        ObjectNode user = out.putObject("settings").putArray("vnext").addObject()
                .put("address", profile.address()).put("port", profile.port())
                .putArray("users").addObject();
        user.put("id", ConfigSupport.text(params, "uuid", ""));
        user.put("encryption", ConfigSupport.text(params, "encryption", "none"));
        String flow = ConfigSupport.text(params, "flow", "");
        if (!flow.isEmpty()) {
            user.put("flow", flow);
        }
        stream(out, profile, params, "none");
    }

    private static void shadowsocks(ObjectNode out, Profile profile, JsonNode params) {
        out.put("protocol", "shadowsocks");
        ObjectNode server = out.putObject("settings").putArray("servers").addObject();
        server.put("address", profile.address());
        server.put("port", profile.port());
        server.put("method", ConfigSupport.text(params, "method", ""));
        server.put("password", ConfigSupport.text(params, "password", ""));
    }

    private static void trojan(ObjectNode out, Profile profile, JsonNode params) {
        out.put("protocol", "trojan");
        ObjectNode server = out.putObject("settings").putArray("servers").addObject();
        server.put("address", profile.address());
        server.put("port", profile.port());
        server.put("password", ConfigSupport.text(params, "password", ""));
        stream(out, profile, params, "tls");
    }

    private static void stream(ObjectNode out, Profile profile, JsonNode params, String defaultSecurity) {
        ObjectNode stream = out.putObject("streamSettings");
        String network = ConfigSupport.text(params, "type", "tcp");
        stream.put("network", network);
        String security = ConfigSupport.text(params, "security", defaultSecurity);
        stream.put("security", security);

        if ("reality".equals(security)) {
            ObjectNode reality = stream.putObject("realitySettings");
            reality.put("serverName", ConfigSupport.text(params, "sni", ""));
            reality.put("fingerprint", ConfigSupport.text(params, "fp", "chrome"));
            reality.put("publicKey", ConfigSupport.text(params, "pbk", ""));
            reality.put("shortId", ConfigSupport.text(params, "sid", ""));
            if (ConfigSupport.has(params, "spx")) {
                reality.put("spiderX", ConfigSupport.text(params, "spx", ""));
            }
        } else if ("tls".equals(security)) {
            ObjectNode tls = stream.putObject("tlsSettings");
            tls.put("serverName", ConfigSupport.text(params, "sni", profile.address()));
            tls.put("fingerprint", ConfigSupport.text(params, "fp", "chrome"));
            if (ConfigSupport.has(params, "alpn")) {
                tls.putArray("alpn").add(ConfigSupport.text(params, "alpn", ""));
            }
            tls.put("allowInsecure", "1".equals(ConfigSupport.text(params, "allowInsecure", "0"))
                    || "true".equals(ConfigSupport.text(params, "insecure", "false")));
        }

        if ("ws".equals(network)) {
            ObjectNode ws = stream.putObject("wsSettings");
            ws.put("path", ConfigSupport.text(params, "path", "/"));
            if (ConfigSupport.has(params, "host")) {
                ws.putObject("headers").put("Host", ConfigSupport.text(params, "host", ""));
            }
        } else if ("grpc".equals(network)) {
            stream.putObject("grpcSettings").put("serviceName", ConfigSupport.text(params, "serviceName", ""));
        }
    }
}
