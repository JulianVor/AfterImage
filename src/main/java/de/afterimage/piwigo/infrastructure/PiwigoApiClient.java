package de.afterimage.piwigo.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.piwigo.application.PiwigoException;
import de.afterimage.piwigo.application.PiwigoGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PiwigoApiClient implements PiwigoGateway {
    private final AfterimageProperties.Piwigo configuration;
    private final ObjectMapper json;
    private final HttpClient http;
    private volatile boolean authenticated;

    @Autowired
    public PiwigoApiClient(AfterimageProperties properties, ObjectMapper json) {
        this(properties.piwigo(), json);
    }

    PiwigoApiClient(AfterimageProperties.Piwigo configuration, ObjectMapper json) {
        this.configuration = configuration;
        this.json = json;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder().connectTimeout(configuration.connectTimeout())
                .cookieHandler(cookies).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public List<Album> albums() {
        JsonNode categories = call("pwg.categories.getList", Map.of("recursive", "true"))
                .path("categories");
        List<Album> result = new ArrayList<>();
        categories.forEach(item -> result.add(new Album(item.path("id").asLong(), text(item, "name"),
                item.hasNonNull("id_uppercat") ? item.path("id_uppercat").asLong() : null,
                safeUrl(firstText(item, "url", "page_url")), item.path("nb_images").asInt(0))));
        return List.copyOf(result);
    }

    @Override
    public ImagePage images(long albumId, int page, int pageSize) {
        return images(albumId, page, pageSize, null);
    }

    @Override
    public ImagePage randomImages(long albumId, int pageSize) {
        return images(albumId, 0, pageSize, "random");
    }

    @Override
    public Image image(long albumId, long imageId) {
        Map<Long, Album> albumsById = albumMap();
        JsonNode item = call("pwg.images.getInfo", Map.of("image_id", Long.toString(imageId)));
        return image(item, albumId, albumsById);
    }

    private ImagePage images(long albumId, int page, int pageSize, String order) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        Map<Long, Album> albumsById = albumMap();
        java.util.LinkedHashMap<String, String> parameters = new java.util.LinkedHashMap<>();
        parameters.put("cat_id[]", Long.toString(albumId));
        parameters.put("recursive", "true");
        parameters.put("page", Integer.toString(safePage));
        parameters.put("per_page", Integer.toString(safeSize));
        if (order != null) parameters.put("order", order);
        JsonNode result = call("pwg.categories.getImages", parameters);
        List<Image> images = new ArrayList<>();
        result.path("images").forEach(item -> images.add(image(item, albumId, albumsById)));
        JsonNode paging = result.path("paging");
        int total = paging.path("total_count").asInt(images.size());
        int pages = Math.max(1, (total + safeSize - 1) / safeSize);
        return new ImagePage(List.copyOf(images), safePage, safeSize, total, pages);
    }

    private Map<Long, Album> albumMap() {
        Map<Long, Album> albumsById = new HashMap<>();
        albums().forEach(album -> albumsById.put(album.id(), album));
        return albumsById;
    }

    private Image image(JsonNode item, long rootAlbumId, Map<Long, Album> albumsById) {
        JsonNode derivatives = item.path("derivatives");
        String element = safeUrl(firstText(item, "element_url", "element"));
        Album imageAlbum = mostSpecificAlbum(item.path("categories"), rootAlbumId, albumsById);
        String pageUrl = imageAlbum == null ? safeUrl(firstText(item, "page_url", "url"))
                : categoryPageUrl(item.path("categories"), imageAlbum.id(), firstText(item, "page_url", "url"));
        return new Image(item.path("id").asLong(), firstText(item, "name", "file"),
                imageAlbum == null ? null : imageAlbum.name(),
                // Grid tiles render considerably larger than Piwigo's own square/thumb crop (~120px),
                // so use the proportionally-resized "medium" derivative to avoid visible upscaling blur.
                derivative(derivatives, "medium", "small", "large", element),
                derivative(derivatives, "square", "thumb", "small", element),
                derivative(derivatives, "xlarge", "large", "medium", element),
                pageUrl, integer(item, "width"), integer(item, "height"));
    }

    private Album mostSpecificAlbum(JsonNode categories, long rootAlbumId, Map<Long, Album> albumsById) {
        List<Album> candidates = new ArrayList<>();
        categories.forEach(category -> {
            Album album = albumsById.get(category.path("id").asLong());
            if (album != null && belongsTo(album, rootAlbumId, albumsById)) candidates.add(album);
        });
        return candidates.stream().max(Comparator.comparingInt(album -> depth(album, albumsById))).orElse(null);
    }

    private static boolean belongsTo(Album album, long rootAlbumId, Map<Long, Album> albumsById) {
        Album current = album;
        while (current != null) {
            if (current.id() == rootAlbumId) return true;
            current = current.parentId() == null ? null : albumsById.get(current.parentId());
        }
        return false;
    }

    private static int depth(Album album, Map<Long, Album> albumsById) {
        int depth = 0;
        Album current = album;
        while (current.parentId() != null && albumsById.containsKey(current.parentId())) {
            depth++;
            current = albumsById.get(current.parentId());
        }
        return depth;
    }

    private String categoryPageUrl(JsonNode categories, long albumId, String fallback) {
        for (JsonNode category : categories) {
            if (category.path("id").asLong() == albumId) return safeUrl(firstText(category, "page_url", "url"));
        }
        return safeUrl(fallback);
    }

    private synchronized void authenticateIfNeeded() {
        if (configuration.apiKeyConfigured() || !configuration.credentialsConfigured() || authenticated) return;
        send("pwg.session.login", Map.of("username", configuration.username(),
                "password", configuration.password()));
        authenticated = true;
    }

    private JsonNode call(String method, Map<String, String> parameters) {
        authenticateIfNeeded();
        return send(method, parameters);
    }

    private JsonNode send(String method, Map<String, String> parameters) {
        String form = encode(withMethod(method, parameters));
        String separator = configuration.apiUrl().getQuery() == null ? "?" : "&";
        URI endpoint = URI.create(configuration.apiUrl() + separator + "format=json");
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(configuration.requestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (configuration.apiKeyConfigured()) builder.header("X-PIWIGO-API", configuration.apiKey().trim());
        HttpRequest request = builder.build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new PiwigoException("Piwigo antwortet mit HTTP " + response.statusCode() + ".");
            JsonNode root = json.readTree(response.body());
            if (!"ok".equalsIgnoreCase(root.path("stat").asText()))
                throw new PiwigoException(root.path("message").asText("Piwigo hat die Anfrage abgelehnt."));
            return root.path("result");
        } catch (IOException exception) {
            throw new PiwigoException("Die Piwigo-Antwort ist ungültig oder nicht erreichbar.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PiwigoException("Die Piwigo-Anfrage wurde unterbrochen.", exception);
        }
    }

    private static Map<String, String> withMethod(String method, Map<String, String> parameters) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        values.put("method", method); values.putAll(parameters); return values;
    }
    private static String encode(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }
    private String derivative(JsonNode derivatives, String first, String second, String third, String fallback) {
        for (String key : List.of(first, second, third)) {
            String url = derivatives.path(key).path("url").asText(null);
            if (url != null && !url.isBlank()) return safeUrl(url);
        }
        return fallback;
    }
    private String safeUrl(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            URI value = configuration.baseUrl().resolve(raw);
            return "http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme())
                    ? value.toString() : "";
        } catch (IllegalArgumentException ignored) { return ""; }
    }
    private static String text(JsonNode node, String name) { return node.path(name).asText(""); }
    private static String firstText(JsonNode node, String... names) {
        for (String name : names) { String value = node.path(name).asText(null); if (value != null && !value.isBlank()) return value; }
        return "";
    }
    private static Integer integer(JsonNode node, String name) { return node.hasNonNull(name) ? node.path(name).asInt() : null; }
}
