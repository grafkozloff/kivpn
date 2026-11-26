package org.kivpn.vpn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public final class SingBoxConfigGenerator implements ConfigGenerator {

    @Override
    public String moduleType() {
        return "sing-box";
    }

    @Override
    public String generate(Profile profile) {
        JsonNode params = ConfigSupport.params(profile);
        ObjectNode root = ConfigSupport.JSON.createObjectNode();
        root.putObject("log").put("level", "warn");

        ArrayNode inbounds = root.putArray("inbounds");
        ObjectNode mixed = inbounds.addObject();
        mixed.put("type", "mixed");
        mixed.put("tag", "mixed-in");
        mixed.put("listen", "127.0.0.1");
        mixed.put("listen_port", 2080);
        ObjectNode tun = inbounds.addObject();
        tun.put("type", "tun");
        tun.put("tag", "tun-in");
        tun.putArray("address").add("172.19.0.1/30").add("fdfe:dcba:9876::1/126");
        tun.put("mtu", 9000);
        tun.put("auto_route", true);
        tun.put("strict_route", true);
        tun.put("stack", "system");
        tun.put("sniff", true);

        ArrayNode outbounds = root.putArray("outbounds");
        outbounds.add(proxyOutbound(profile, params));
        outbounds.addObject().put("type", "direct").put("tag", "direct");
        outbounds.addObject().put("type", "block").put("tag", "block");
        outbounds.addObject().put("type", "dns").put("tag", "dns-out");

        ObjectNode dns = root.putObject("dns");
        ObjectNode remote = dns.putArray("servers").addObject();
        remote.put("tag", "remote");
        remote.put("address", "https://1.1.1.1/dns-query");
        remote.put("detour", "proxy");
        dns.put("final", "remote");

        ObjectNode route = root.putObject("route");
        ArrayNode rules = route.putArray("rules");
        ObjectNode dnsRule = rules.addObject();
        dnsRule.put("protocol", "dns");
        dnsRule.put("outbound", "dns-out");
        ObjectNode privateRule = rules.addObject();
        privateRule.put("ip_is_private", true);
        privateRule.put("outbound", "direct");
        route.put("final", "proxy");
        route.put("auto_detect_interface", true);

        return root.toPrettyString();
    }

    private static ObjectNode proxyOutbound(Profile profile, JsonNode params) {
        ObjectNode out = ConfigSupport.JSON.createObjectNode();
        out.put("tag", "proxy");
        out.put("server", profile.address());
        out.put("server_port", profile.port());
        switch (profile.protocol()) {
            case "vless" -> vless(out, profile, params);
            case "ss" -> shadowsocks(out, params);
            case "trojan" -> trojan(out, profile, params);
            default -> throw new IllegalArgumentException("Неподдерживаемый протокол: " + profile.protocol());
        }
        return out;
    }

    private static void vless(ObjectNode out, Profile profile, JsonNode params) {
        out.put("type", "vless");
        out.put("uuid", ConfigSupport.text(params, "uuid", ""));
        String flow = ConfigSupport.text(params, "flow", "");
        if (!flow.isEmpty()) {
            out.put("flow", flow);
        }
        String security = ConfigSupport.text(params, "security", "none");
        if ("tls".equals(security) || "reality".equals(security)) {
            ObjectNode tls = out.putObject("tls");
            tls.put("enabled", true);
            tls.put("server_name", ConfigSupport.text(params, "sni", profile.address()));
            fingerprint(tls, params);
            if ("reality".equals(security)) {
                ObjectNode reality = tls.putObject("reality");
                reality.put("enabled", true);
                reality.put("public_key", ConfigSupport.text(params, "pbk", ""));
                reality.put("short_id", ConfigSupport.text(params, "sid", ""));
            }
        }
        transport(out, params);
    }

    private static void shadowsocks(ObjectNode out, JsonNode params) {
        out.put("type", "shadowsocks");
        out.put("method", ConfigSupport.text(params, "method", ""));
        out.put("password", ConfigSupport.text(params, "password", ""));
    }

    private static void trojan(ObjectNode out, Profile profile, JsonNode params) {
        out.put("type", "trojan");
        out.put("password", ConfigSupport.text(params, "password", ""));
        ObjectNode tls = out.putObject("tls");
        tls.put("enabled", true);
        tls.put("server_name", ConfigSupport.text(params, "sni", profile.address()));
        fingerprint(tls, params);
        transport(out, params);
    }

    private static void fingerprint(ObjectNode tls, JsonNode params) {
        String fp = ConfigSupport.text(params, "fp", "");
        if (!fp.isEmpty()) {
            tls.putObject("utls").put("enabled", true).put("fingerprint", fp);
        }
    }

    private static void transport(ObjectNode out, JsonNode params) {
        String network = ConfigSupport.text(params, "type", "tcp");
        if ("ws".equals(network)) {
            ObjectNode ws = out.putObject("transport");
            ws.put("type", "ws");
            ws.put("path", ConfigSupport.text(params, "path", "/"));
            if (ConfigSupport.has(params, "host")) {
                ws.putObject("headers").put("Host", ConfigSupport.text(params, "host", ""));
            }
        } else if ("grpc".equals(network)) {
            ObjectNode grpc = out.putObject("transport");
            grpc.put("type", "grpc");
            grpc.put("service_name", ConfigSupport.text(params, "serviceName", ""));
        }
    }
}
