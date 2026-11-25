package org.kivpn.source;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SourceRepository;
import org.kivpn.model.Profile;
import org.kivpn.model.Source;
import org.kivpn.parser.ProfileParseException;
import org.kivpn.parser.ProfileParserDispatcher;
import org.springframework.stereotype.Component;

@Component
public class SourceService {

    public record UpdateSummary(int added, int skipped, List<String> errors) {
    }

    private final SourceRepository sources;
    private final ProfileRepository profiles;
    private final SourceProviderDispatcher providers;
    private final ProfileParserDispatcher parsers;

    public SourceService(SourceRepository sources, ProfileRepository profiles,
                         SourceProviderDispatcher providers, ProfileParserDispatcher parsers) {
        this.sources = sources;
        this.profiles = profiles;
        this.providers = providers;
        this.parsers = parsers;
    }

    public Source addSource(String name, String type, String url) throws SourceException {
        try {
            String resolvedType = type.toLowerCase(Locale.ROOT);
            if (!providers.supports(resolvedType)) {
                throw new SourceException("Неподдерживаемый тип источника: " + type
                        + ". Поддерживаются: " + providers.supportedTypes() + ".");
            }
            if (url == null || url.isBlank()) {
                throw new SourceException("Не указан URL или путь источника.");
            }
            String resolvedName = (name == null || name.isBlank()) ? resolvedType + " " + url : name;
            long id = sources.add(new Source(null, resolvedName, resolvedType, url, true));
            return sources.findById(id).orElseThrow();
        } catch (SQLException e) {
            throw new SourceException("Не удалось добавить источник: " + e.getMessage(), e);
        }
    }

    public List<Source> listSources() throws SourceException {
        try {
            return sources.findAll();
        } catch (SQLException e) {
            throw new SourceException("Не удалось получить источники: " + e.getMessage(), e);
        }
    }

    public void setEnabled(long id, boolean enabled) throws SourceException {
        try {
            requireSource(id);
            sources.setEnabled(id, enabled);
        } catch (SQLException e) {
            throw new SourceException("Не удалось изменить источник: " + e.getMessage(), e);
        }
    }

    public void removeSource(long id) throws SourceException {
        try {
            requireSource(id);
            sources.delete(id);
        } catch (SQLException e) {
            throw new SourceException("Не удалось удалить источник: " + e.getMessage(), e);
        }
    }

    public UpdateSummary update(Long sourceId) throws SourceException {
        try {
            List<Source> toUpdate;
            if (sourceId != null) {
                toUpdate = List.of(requireSource(sourceId));
            } else {
                toUpdate = sources.findAll().stream().filter(Source::enabled).toList();
            }
            int added = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();
            for (Source source : toUpdate) {
                try {
                    for (String link : providers.fetch(source)) {
                        try {
                            Profile parsed = parsers.parse(link);
                            if (profiles.findByUri(parsed.uri()).isPresent()) {
                                skipped++;
                                continue;
                            }
                            profiles.add(new Profile(null, source.id(), parsed.name(), parsed.protocol(),
                                    parsed.address(), parsed.port(), parsed.uri(), parsed.params(), false));
                            added++;
                        } catch (ProfileParseException e) {
                            errors.add("Источник " + source.id() + ": " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    errors.add("Источник " + source.id() + " (" + source.name() + "): " + e.getMessage());
                }
            }
            return new UpdateSummary(added, skipped, errors);
        } catch (SQLException e) {
            throw new SourceException("Не удалось обновить источники: " + e.getMessage(), e);
        }
    }

    private Source requireSource(long id) throws SQLException, SourceException {
        return sources.findById(id)
                .orElseThrow(() -> new SourceException("Источник с id " + id + " не найден."));
    }
}
