package org.kivpn.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class ParserSupport {

    record HostPort(String host, int port) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private ParserSupport() {
    }

    // Разбираем вручную: java.net.URI отклоняет реальные ссылки с неэкранированными
    // символами и возвращает null для host с подчёркиванием.
    static HostPort authority(String authority) throws ProfileParseException {
        if (authority == null || authority.isEmpty()) {
            throw new ProfileParseException("Отсутствует адрес сервера.");
        }
        String host;
        String portText;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                throw new ProfileParseException("Некорректный IPv6-адрес.");
            }
            host = authority.substring(1, close);
            String rest = authority.substring(close + 1);
            if (!rest.startsWith(":")) {
                throw new ProfileParseException("Отсутствует порт.");
            }
            portText = rest.substring(1);
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon < 0) {
                throw new ProfileParseException("Отсутствует порт.");
            }
            host = authority.substring(0, colon);
            portText = authority.substring(colon + 1);
        }
        if (host.isEmpty()) {
            throw new ProfileParseException("Пустой адрес сервера.");
        }
        return new HostPort(host, port(portText));
    }

    private static int port(String text) throws ProfileParseException {
        if (text.isEmpty()) {
            throw new ProfileParseException("Отсутствует порт.");
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ProfileParseException("Некорректный порт: " + text);
        }
        if (value < 1 || value > 65535) {
            throw new ProfileParseException("Порт вне диапазона 1..65535: " + value);
        }
        return value;
    }

    static Map<String, String> query(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            if (key.isEmpty()) {
                continue;
            }
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    static String base64Decode(String value) throws ProfileParseException {
        String normalized = value.replaceAll("\\s", "").replace('-', '+').replace('_', '/');
        int padding = (4 - normalized.length() % 4) % 4;
        normalized += "=".repeat(padding);
        try {
            return new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ProfileParseException("Некорректная base64-строка в ссылке.");
        }
    }

    static String toJson(Map<String, String> params) throws ProfileParseException {
        try {
            return JSON.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new ProfileParseException("Не удалось сохранить параметры профиля.");
        }
    }
}
