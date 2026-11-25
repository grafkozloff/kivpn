package org.kivpn.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kivpn.model.Source;
import org.springframework.stereotype.Component;

@Component
public class FileSourceProvider implements SourceProvider {

    @Override
    public String type() {
        return "file";
    }

    @Override
    public List<String> fetch(Source source) throws IOException {
        List<String> links = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(source.url()), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("://")) {
                links.add(trimmed);
            }
        }
        return links;
    }
}
