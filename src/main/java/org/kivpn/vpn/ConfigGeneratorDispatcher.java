package org.kivpn.vpn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public class ConfigGeneratorDispatcher {

    private final Map<String, ConfigGenerator> byType;

    public ConfigGeneratorDispatcher(List<ConfigGenerator> generators) {
        Map<String, ConfigGenerator> map = new LinkedHashMap<>();
        for (ConfigGenerator generator : generators) {
            map.put(generator.moduleType().toLowerCase(Locale.ROOT), generator);
        }
        this.byType = map;
    }

    public String generate(String moduleType, Profile profile) {
        ConfigGenerator generator = byType.get(moduleType.toLowerCase(Locale.ROOT));
        if (generator == null) {
            throw new IllegalArgumentException("Неподдерживаемый тип модуля: " + moduleType
                    + ". Поддерживаются: " + String.join(", ", byType.keySet()) + ".");
        }
        return generator.generate(profile);
    }
}
