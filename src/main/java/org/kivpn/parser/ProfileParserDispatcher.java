package org.kivpn.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public class ProfileParserDispatcher {

    private final Map<String, ProfileParser> byScheme;

    public ProfileParserDispatcher(List<ProfileParser> parsers) {
        Map<String, ProfileParser> map = new LinkedHashMap<>();
        for (ProfileParser parser : parsers) {
            map.put(parser.scheme().toLowerCase(Locale.ROOT), parser);
        }
        this.byScheme = map;
    }

    public Profile parse(String link) throws ProfileParseException {
        if (link == null || link.isBlank()) {
            throw new ProfileParseException("Пустая ссылка профиля.");
        }
        String trimmed = link.trim();
        int sep = trimmed.indexOf("://");
        if (sep <= 0) {
            throw new ProfileParseException("Ссылка должна содержать протокол, например vless://, ss:// или trojan://.");
        }
        String scheme = trimmed.substring(0, sep).toLowerCase(Locale.ROOT);
        ProfileParser parser = byScheme.get(scheme);
        if (parser == null) {
            throw new ProfileParseException("Неподдерживаемый протокол: " + scheme
                    + ". Поддерживаются: " + String.join(", ", byScheme.keySet()) + ".");
        }
        return parser.parse(trimmed);
    }
}
