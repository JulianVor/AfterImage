package de.afterimage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "afterimage")
public record AfterimageProperties(Site site, Wiki wiki, Piwigo piwigo, Media media, Admin admin, Import importer) {

    public AfterimageProperties {
        site = site == null ? new Site("FotosVorJu", "Julian Vornfeld", "julian-vornfeld", "Film · Photography · Visual Worlds") : site;
        wiki = wiki == null ? new Wiki(
                URI.create("https://wiki.fotosvorju.de"),
                URI.create("https://wiki.fotosvorju.de/w/api.php"),
                null,
                null,
                null,
                true,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30)) : wiki;
        piwigo = piwigo == null ? new Piwigo(true,
                URI.create("https://galerie.fotosvorju.de"),
                URI.create("https://galerie.fotosvorju.de/ws.php"), null, null, null,
                50, Duration.ofSeconds(10), Duration.ofSeconds(30)) : piwigo;
        media = media == null ? new Media(Path.of("media")) : media;
        admin = admin == null ? new Admin("admin", "change-me-locally") : admin;
        importer = importer == null ? new Import(8_000) : importer;
    }
    public record Piwigo(boolean enabled, URI baseUrl, URI apiUrl, String apiKey, String username, String password, int defaultPageSize,
                         Duration connectTimeout, Duration requestTimeout) {
        public Piwigo {
            baseUrl = baseUrl == null ? URI.create("https://galerie.fotosvorju.de") : baseUrl;
            apiUrl = apiUrl == null ? baseUrl.resolve("/ws.php") : apiUrl;
            defaultPageSize = defaultPageSize < 1 ? 50 : Math.min(defaultPageSize, 100);
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        }

        public boolean credentialsConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        public boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Site(String title, String owner, String ownerSlug, String subtitle) {}
    public record Wiki(URI baseUrl, URI apiUrl, String username, String password, String syncCategory,
                       boolean contentEnabled,
                       Duration connectTimeout, Duration requestTimeout) {
        public Wiki {
            baseUrl = baseUrl == null ? URI.create("https://wiki.fotosvorju.de") : baseUrl;
            apiUrl = apiUrl == null ? baseUrl.resolve("/w/api.php") : apiUrl;
            syncCategory = syncCategory == null || syncCategory.isBlank() ? null : syncCategory.trim();
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        }

        public boolean credentialsConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
    public record Media(Path root) {}
    public record Admin(String username, String password) {}
    public record Import(int maxPages) {}
}
