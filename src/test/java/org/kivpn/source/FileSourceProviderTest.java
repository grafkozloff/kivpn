package org.kivpn.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.model.Source;

class FileSourceProviderTest {

    @TempDir
    Path tempDir;

    private final FileSourceProvider provider = new FileSourceProvider();

    @Test
    void fetchReadsLinkLinesAndSkipsComments() throws Exception {
        Path file = tempDir.resolve("profiles.txt");
        String vless = "vless://11111111-2222-3333-4444-555555555555@example.com:443#A";
        String trojan = "trojan://secret@example.org:443#B";
        Files.writeString(file, "# comment\n\n" + vless + "\nnot a link\n" + trojan + "\n");

        List<String> links = provider.fetch(new Source(1L, "File", "file", file.toString(), true));
        assertEquals(List.of(vless, trojan), links);
    }

    @Test
    void fetchMissingFileThrows() {
        Source source = new Source(1L, "File", "file", tempDir.resolve("missing.txt").toString(), true);
        assertThrows(IOException.class, () -> provider.fetch(source));
    }

    @Test
    void typeIsFile() {
        assertEquals("file", provider.type());
    }
}
