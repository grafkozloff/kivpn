package org.kivpn.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public final class SsParser implements ProfileParser {

    private static final String SCHEME = "ss://";

    @Override
    public String scheme() {
        return "ss";
    }

    @Override
    public Profile parse(String link) throws ProfileParseException {
        if (link == null) {
            throw new ProfileParseException("Пустая ссылка Shadowsocks.");
        }
        String uri = link.trim();
        if (!uri.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            throw new ProfileParseException("Ссылка должна начинаться с ss://");
        }
        String body = uri.substring(SCHEME.length());
        if (body.isEmpty()) {
            throw new ProfileParseException("Пустая ссылка Shadowsocks.");
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

        String credentials;
        String authority;
        int at = body.lastIndexOf('@');
        if (at >= 0) {
            credentials = ParserSupport.base64Decode(body.substring(0, at));
            authority = body.substring(at + 1);
        } else {
            String decoded = ParserSupport.base64Decode(body);
            int sep = decoded.lastIndexOf('@');
            if (sep < 0) {
                throw new ProfileParseException("В ссылке Shadowsocks отсутствует адрес сервера.");
            }
            credentials = decoded.substring(0, sep);
            authority = decoded.substring(sep + 1);
        }

        int colon = credentials.indexOf(':');
        if (colon < 0) {
            throw new ProfileParseException("В ссылке Shadowsocks отсутствует метод шифрования.");
        }
        String method = credentials.substring(0, colon);
        String password = credentials.substring(colon + 1);
        if (method.isEmpty()) {
            throw new ProfileParseException("В ссылке Shadowsocks пустой метод шифрования.");
        }
        if (password.isEmpty()) {
            throw new ProfileParseException("В ссылке Shadowsocks пустой пароль.");
        }
        ParserSupport.HostPort hostPort = ParserSupport.authority(authority);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", method);
        params.put("password", password);
        params.putAll(ParserSupport.query(query));

        String resolvedName = (name == null || name.isEmpty()) ? hostPort.host() : name;
        return new Profile(null, null, resolvedName, "ss", hostPort.host(), hostPort.port(),
                uri, ParserSupport.toJson(params), false);
    }
}
