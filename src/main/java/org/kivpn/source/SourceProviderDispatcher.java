package org.kivpn.source;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kivpn.model.Source;
import org.springframework.stereotype.Component;

@Component
public class SourceProviderDispatcher {

    private final Map<String, SourceProvider> byType;

    public SourceProviderDispatcher(List<SourceProvider> providers) {
        Map<String, SourceProvider> map = new LinkedHashMap<>();
        for (SourceProvider provider : providers) {
            map.put(provider.type().toLowerCase(Locale.ROOT), provider);
        }
        this.byType = map;
    }

    public boolean supports(String type) {
        return byType.containsKey(type.toLowerCase(Locale.ROOT));
    }

    public String supportedTypes() {
        return String.join(", ", byType.keySet());
    }

    public List<String> fetch(Source source) throws IOException {
        SourceProvider provider = byType.get(source.type().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IOException("Неподдерживаемый тип источника: " + source.type()
                    + ". Поддерживаются: " + supportedTypes() + ".");
        }
        return provider.fetch(source);
    }
}
