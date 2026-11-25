package org.kivpn.vpn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kivpn.model.Profile;

final class ConfigSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private ConfigSupport() {
    }

    static JsonNode params(Profile profile) {
        try {
            if (profile.params() == null || profile.params().isBlank()) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(profile.params());
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    static String text(JsonNode node, String key, String fallback) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text.isEmpty() ? fallback : text;
    }

    static boolean has(JsonNode node, String key) {
        return node.hasNonNull(key) && !node.get(key).asText().isEmpty();
    }
}
