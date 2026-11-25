package org.kivpn.source;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.kivpn.model.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionSourceProvider implements SourceProvider {

    public interface Fetcher {
        String fetch(String url) throws Exception;
    }

    private final Fetcher fetcher;

    @Autowired
    public SubscriptionSourceProvider(@Value("${kivpn.source.timeout-ms:10000}") int timeoutMs) {
        this(defaultFetcher(timeoutMs));
    }

    public SubscriptionSourceProvider(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String type() {
        return "subscription";
    }

    @Override
    public List<String> fetch(Source source) throws IOException {
        try {
            return parseLinks(fetcher.fetch(source.url()));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Не удалось загрузить подписку: " + e.getMessage(), e);
        }
    }

    static List<String> parseLinks(String content) {
        String text = content == null ? "" : content.trim();
        if (!text.contains("://")) {
            try {
                String decoded = new String(Base64.getMimeDecoder().decode(text), StandardCharsets.UTF_8);
                if (decoded.contains("://")) {
                    text = decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // не base64 — оставляем как есть
            }
        }
        List<String> links = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("://")) {
                links.add(trimmed);
            }
        }
        return links;
    }

    private static Fetcher defaultFetcher(int timeoutMs) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return url -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " для " + url);
            }
            return response.body();
        };
    }
}
