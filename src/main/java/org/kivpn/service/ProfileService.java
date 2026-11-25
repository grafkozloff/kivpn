package org.kivpn.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.kivpn.db.CheckRepository;
import org.kivpn.db.ProfileRepository;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.kivpn.parser.ProfileParseException;
import org.kivpn.parser.ProfileParserDispatcher;
import org.springframework.stereotype.Component;

@Component
public class ProfileService {

    public record ImportSummary(int imported, List<String> errors) {
    }

    private final ProfileParserDispatcher parser;
    private final ProfileRepository profiles;
    private final ServerChecker checker;
    private final CheckRepository checks;
    private final ServerSelector selector;

    public ProfileService(ProfileParserDispatcher parser, ProfileRepository profiles,
                          ServerChecker checker, CheckRepository checks, ServerSelector selector) {
        this.parser = parser;
        this.profiles = profiles;
        this.checker = checker;
        this.checks = checks;
        this.selector = selector;
    }

    public Profile add(String link) throws ProfileParseException, SQLException {
        Profile parsed = parser.parse(link);
        long id = profiles.add(parsed);
        return new Profile(id, parsed.sourceId(), parsed.name(), parsed.protocol(), parsed.address(),
                parsed.port(), parsed.uri(), parsed.params(), parsed.active());
    }

    public ImportSummary importFile(String path) throws IOException, SQLException {
        List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                add(line);
                imported++;
            } catch (ProfileParseException e) {
                errors.add("Строка " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new ImportSummary(imported, errors);
    }

    public List<Profile> list() throws SQLException {
        return profiles.findAll();
    }

    public Optional<Profile> find(long id) throws SQLException {
        return profiles.findById(id);
    }

    public CheckResult check(Profile profile) throws SQLException {
        CheckResult result = checker.check(profile);
        long id = checks.add(result);
        return new CheckResult(id, result.profileId(), result.status(), result.latencyMs(),
                result.checkedAt(), result.error());
    }

    public Map<Long, CheckResult> latestChecks() throws SQLException {
        return checks.findLatestPerProfile();
    }

    public void use(long id) throws SQLException {
        profiles.setActive(id);
    }

    public Optional<Profile> useAuto() throws SQLException {
        Optional<Profile> best = selector.selectBest(profiles.findAll(), checks.findLatestPerProfile());
        if (best.isPresent()) {
            profiles.setActive(best.get().id());
        }
        return best;
    }

    public void exportToFile(Profile profile, String path) throws IOException {
        Files.writeString(Path.of(path), profile.uri() + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public int exportAllToFile(String path) throws SQLException, IOException {
        List<Profile> all = profiles.findAll();
        StringBuilder content = new StringBuilder();
        for (Profile profile : all) {
            content.append(profile.uri()).append(System.lineSeparator());
        }
        Files.writeString(Path.of(path), content.toString(), StandardCharsets.UTF_8);
        return all.size();
    }
}
