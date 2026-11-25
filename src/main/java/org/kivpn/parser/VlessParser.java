package org.kivpn.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public final class VlessParser implements ProfileParser {

    private static final String SCHEME = "vless://";

    @Override
    public String scheme() {
        return "vless";
    }

    @Override
    public Profile parse(String link) throws ProfileParseException {
        if (link == null) {
            throw new ProfileParseException("Пустая ссылка VLESS.");
        }
        String uri = link.trim();
        if (!uri.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            throw new ProfileParseException("Ссылка должна начинаться с vless://");
        }
        String body = uri.substring(SCHEME.length());
        if (body.isEmpty()) {
            throw new ProfileParseException("Пустая ссылка VLESS.");
        }

        String name = null;
        int hash = body.indexOf('#');
        if (hash >= 0) {
            name = ParserSupport.urlDecode(body.substring(hash + 1));
            body = body.substring(0, hash);
        }
        String query = null;
        int question = body.indexOf('?');
        if (question >= 0) {
            query = body.substring(question + 1);
            body = body.substring(0, question);
        }

        int at = body.lastIndexOf('@');
        if (at < 0) {
            throw new ProfileParseException("В ссылке VLESS отсутствует uuid (часть до @).");
        }
        String uuid = body.substring(0, at);
        if (uuid.isEmpty()) {
            throw new ProfileParseException("В ссылке VLESS пустой uuid.");
        }
        ParserSupport.HostPort hostPort = ParserSupport.authority(body.substring(at + 1));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("uuid", uuid);
        params.putAll(ParserSupport.query(query));

        String resolvedName = (name == null || name.isEmpty()) ? hostPort.host() : name;
        return new Profile(null, null, resolvedName, "vless", hostPort.host(), hostPort.port(),
                uri, ParserSupport.toJson(params), false);
    }
}
