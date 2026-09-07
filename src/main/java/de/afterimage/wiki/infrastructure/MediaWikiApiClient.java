package de.afterimage.wiki.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.wiki.application.MediaWikiException;
import de.afterimage.wiki.application.MediaWikiGateway;
import de.afterimage.wiki.domain.MediaWikiArticle;
import de.afterimage.wiki.domain.MediaWikiCategoryInfo;
import de.afterimage.wiki.domain.MediaWikiEditResult;
import de.afterimage.wiki.domain.MediaWikiImage;
import de.afterimage.wiki.domain.MediaWikiSearchHit;
import de.afterimage.wiki.domain.MediaWikiSiteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.function.Function;

import static de.afterimage.wiki.application.MediaWikiException.Kind;

@Component
public class MediaWikiApiClient implements MediaWikiGateway {
    private static final Logger log = LoggerFactory.getLogger(MediaWikiApiClient.class);
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024;

    private final AfterimageProperties.Wiki configuration;
    private final ObjectMapper objectMapper;
    private final CookieManager cookies;
    private final HttpClient httpClient;
    private volatile boolean authenticated;

    @Autowired
    public MediaWikiApiClient(AfterimageProperties properties, ObjectMapper objectMapper) {
        this(properties.wiki(), objectMapper);
    }

    MediaWikiApiClient(AfterimageProperties.Wiki configuration, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        this.cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public synchronized void authenticate() {
        requireConfiguration();
        cookies.getCookieStore().removeAll();
        authenticated = false;

        JsonNode tokenResponse = sendGet(Map.of(
                "action", "query", "meta", "tokens", "type", "login", "format", "json"),
                "login token");
        rejectApiError(tokenResponse);
        String token = requiredText(tokenResponse.at("/query/tokens/logintoken"), "login token");

        Map<String, String> loginParameters = new LinkedHashMap<>();
        loginParameters.put("action", "login");
        loginParameters.put("lgname", configuration.username());
        loginParameters.put("lgpassword", configuration.password());
        loginParameters.put("lgtoken", token);
        loginParameters.put("format", "json");
        JsonNode loginResponse = sendPost(loginParameters, "login");
        rejectApiError(loginResponse);
        String result = requiredText(loginResponse.at("/login/result"), "login result");
        if (!"Success".equals(result)) {
            throw new MediaWikiException(Kind.AUTHENTICATION_FAILED,
                    "MediaWiki authentication failed (result: " + result + ")");
        }
        authenticated = true;
        log.info("MediaWiki session established");
    }

    @Override
    public MediaWikiSiteInfo getSiteInfo() {
        return authenticatedGet(Map.of("action", "query", "meta", "siteinfo"), "site info", root -> {
            JsonNode general = required(root, "/query/general", "site info");
            return new MediaWikiSiteInfo(text(general, "sitename"), text(general, "generator"),
                    text(general, "base"), text(general, "lang"));
        });
    }

    @Override
    public Optional<MediaWikiArticle> getPage(String title) {
        requireValue(title, "title");
        return authenticatedGet(Map.of(
                "action", "query", "prop", "revisions", "titles", title,
                "rvprop", "ids|timestamp|content", "rvslots", "main"), "page", root -> {
            JsonNode page = firstPage(root);
            if (page.path("missing").asBoolean(false)) {
                return Optional.empty();
            }
            return Optional.of(mapArticle(page));
        });
    }

    @Override
    public List<MediaWikiSearchHit> search(String query) {
        requireValue(query, "query");
        return authenticatedGet(Map.of("action", "query", "list", "search", "srsearch", query), "search", root -> {
            JsonNode results = required(root, "/query/search", "search results");
            if (!results.isArray()) {
                throw invalid("MediaWiki search results are not an array");
            }
            List<MediaWikiSearchHit> hits = new ArrayList<>();
            for (JsonNode hit : results) {
                hits.add(new MediaWikiSearchHit(requiredLong(hit, "pageid"), requiredInt(hit, "ns"),
                        requiredText(hit.get("title"), "search title"), text(hit, "snippet"),
                        optionalInstant(hit, "timestamp")));
            }
            return List.copyOf(hits);
        });
    }

    @Override
    public Optional<String> getRenderedPage(String title) {
        requireValue(title, "title");
        try {
            return authenticatedGet(Map.of("action", "parse", "page", title, "prop", "text"), "rendered page",
                    root -> Optional.of(requiredText(root.at("/parse/text"), "rendered page")));
        } catch (MediaWikiException exception) {
            if (exception.kind() == Kind.API_ERROR && exception.getMessage().contains("missingtitle")) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public List<MediaWikiArticle> getPages(String optionalCategory, int maxPages) {
        if (maxPages < 1) throw new IllegalArgumentException("maxPages must be positive");
        boolean categoryFiltered = optionalCategory != null && !optionalCategory.isBlank();
        String categoryTitle = categoryFiltered
                ? (optionalCategory.contains(":") ? optionalCategory : "Category:" + optionalCategory)
                : null;
        List<MediaWikiArticle> articles = new ArrayList<>();
        Map<String, String> continuation = Map.of();
        do {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("action", "query");
            if (categoryFiltered) {
                parameters.put("generator", "categorymembers");
                parameters.put("gcmtitle", categoryTitle);
                parameters.put("gcmnamespace", "0");
                parameters.put("gcmlimit", "max");
            } else {
                parameters.put("generator", "allpages");
                parameters.put("gapnamespace", "0");
                parameters.put("gaplimit", "max");
            }
            parameters.put("prop", "revisions");
            parameters.put("rvprop", "ids|timestamp|content");
            parameters.put("rvslots", "main");
            parameters.putAll(continuation);

            JsonNode root = authenticatedGet(parameters, categoryFiltered ? "category sync" : "main namespace sync",
                    Function.identity());
            JsonNode pages = root.at("/query/pages");
            if (!pages.isMissingNode()) {
                if (!pages.isArray()) throw invalid("MediaWiki category pages are not an array");
                for (JsonNode page : pages) {
                    articles.add(mapArticle(page));
                    if (articles.size() > maxPages) {
                        throw new MediaWikiException(Kind.API_ERROR,
                                "MediaWiki category exceeds configured page limit of " + maxPages);
                    }
                }
            }
            JsonNode next = root.path("continue");
            if (next.isObject()) {
                Map<String, String> values = new LinkedHashMap<>();
                next.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
                continuation = values;
            } else {
                continuation = Map.of();
            }
        } while (!continuation.isEmpty());
        articles.sort(Comparator.comparing(MediaWikiArticle::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(articles);
    }

    @Override
    public int getMainNamespacePageCount(int maxPages) {
        if (maxPages < 1) throw new IllegalArgumentException("maxPages must be positive");
        int count = 0;
        Map<String, String> continuation = Map.of();
        do {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("action", "query");
            parameters.put("list", "allpages");
            parameters.put("apnamespace", "0");
            parameters.put("aplimit", "max");
            parameters.putAll(continuation);
            JsonNode root = authenticatedGet(parameters, "main namespace count", Function.identity());
            JsonNode pages = required(root, "/query/allpages", "all pages");
            if (!pages.isArray()) throw invalid("MediaWiki all-pages response is not an array");
            count += pages.size();
            if (count > maxPages) {
                throw new MediaWikiException(Kind.API_ERROR,
                        "MediaWiki exceeds configured page limit of " + maxPages);
            }
            continuation = continuation(root);
        } while (!continuation.isEmpty());
        return count;
    }

    @Override
    public Optional<MediaWikiImage> getImage(String fileTitle, int thumbnailWidth) {
        requireValue(fileTitle, "fileTitle");
        if (thumbnailWidth < 1 || thumbnailWidth > 4_000) {
            throw new IllegalArgumentException("thumbnailWidth must be between 1 and 4000");
        }
        Optional<ImageInfo> imageInfo = authenticatedGet(Map.of(
                "action", "query", "prop", "imageinfo", "titles", fileTitle,
                "iiprop", "url|mime|size", "iiurlwidth", Integer.toString(thumbnailWidth)),
                "image info", root -> mapImageInfo(root, fileTitle));
        if (imageInfo.isEmpty()) {
            return Optional.empty();
        }
        ImageInfo info = imageInfo.get();
        if (!info.mimeType().startsWith("image/") || "image/svg+xml".equalsIgnoreCase(info.mimeType())) {
            return Optional.empty();
        }
        try {
            byte[] content = authenticatedDownload(info.downloadUrl(), "Wiki image " + info.filename());
            return Optional.of(new MediaWikiImage(info.title(), info.filename(), info.sourceUrl(), info.mimeType(),
                    info.width(), info.height(), content));
        } catch (MediaWikiException exception) {
            if (exception.kind() != Kind.PERMISSION_DENIED) {
                throw exception;
            }
            ImagePayload payload = downloadImageThroughApi(fileTitle, thumbnailWidth);
            return Optional.of(new MediaWikiImage(info.title(), payload.filename(), info.sourceUrl(),
                    payload.mimeType(), payload.width(), payload.height(), payload.content()));
        }
    }

    private ImagePayload downloadImageThroughApi(String fileTitle, int thumbnailWidth) {
        return authenticatedGet(Map.of(
                "action", "afterimageimage", "title", fileTitle,
                "width", Integer.toString(thumbnailWidth)), "private image content", root -> {
            JsonNode image = required(root, "/afterimageimage", "private image content");
            byte[] content;
            try {
                content = Base64.getDecoder().decode(requiredText(image.get("content"), "encoded image content"));
            } catch (IllegalArgumentException exception) {
                throw invalid("MediaWiki returned invalid encoded image content");
            }
            if (content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw invalid("MediaWiki image is empty or exceeds the 25 MB import limit");
            }
            String mimeType = requiredText(image.get("mime"), "image MIME type");
            if (!mimeType.startsWith("image/") || "image/svg+xml".equalsIgnoreCase(mimeType)) {
                throw invalid("MediaWiki returned an unsupported image MIME type");
            }
            return new ImagePayload(requiredText(image.get("filename"), "image filename"), mimeType,
                    optionalInt(image, "width"), optionalInt(image, "height"), content);
        });
    }

    @Override
    public synchronized MediaWikiEditResult editPage(String title, String text, long baseRevisionId,
                                                     Instant baseTimestamp, String summary) {
        requireValue(title, "title");
        if (text == null) throw new IllegalArgumentException("text must not be null");
        requireValue(summary, "summary");
        ensureAuthenticated();
        try {
            return executeEdit(title, text, baseRevisionId, baseTimestamp, summary);
        } catch (SessionExpiredException expired) {
            log.info("MediaWiki session expired during edit; authenticating once");
            authenticate();
            try {
                return executeEdit(title, text, baseRevisionId, baseTimestamp, summary);
            } catch (SessionExpiredException repeated) {
                throw new MediaWikiException(Kind.AUTHENTICATION_FAILED,
                        "MediaWiki session was rejected after re-authentication");
            }
        }
    }

    private MediaWikiEditResult executeEdit(String title, String text, long baseRevisionId,
                                            Instant baseTimestamp, String summary) {
        JsonNode tokenResponse = executeGet(Map.of(
                "action", "query", "meta", "tokens", "type", "csrf", "curtimestamp", "1"),
                "edit token", Function.identity());
        String csrfToken = requiredText(tokenResponse.at("/query/tokens/csrftoken"), "CSRF token");
        String startTimestamp = requiredText(tokenResponse.get("curtimestamp"), "current timestamp");

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("action", "edit");
        parameters.put("assert", "user");
        parameters.put("format", "json");
        parameters.put("formatversion", "2");
        parameters.put("title", title);
        parameters.put("text", text);
        parameters.put("summary", summary);
        parameters.put("nocreate", "1");
        parameters.put("watchlist", "nochange");
        parameters.put("baserevid", Long.toString(baseRevisionId));
        parameters.put("basetimestamp", baseTimestamp.toString());
        parameters.put("starttimestamp", startTimestamp);
        parameters.put("token", csrfToken);
        JsonNode root = sendPost(parameters, "page edit");
        rejectApiError(root);
        JsonNode edit = required(root, "/edit", "edit result");
        if (!"Success".equals(requiredText(edit.get("result"), "edit result"))) {
            throw invalid("MediaWiki did not confirm the page edit");
        }
        return new MediaWikiEditResult(requiredText(edit.get("title"), "edited title"), baseRevisionId,
                requiredLong(edit, "newrevid"));
    }

    @Override
    public synchronized MediaWikiEditResult createPage(String title, String text, String summary) {
        requireValue(title, "title");
        if (text == null) throw new IllegalArgumentException("text must not be null");
        requireValue(summary, "summary");
        ensureAuthenticated();
        try {
            return executeCreate(title, text, summary);
        } catch (SessionExpiredException expired) {
            log.info("MediaWiki session expired during page creation; authenticating once");
            authenticate();
            try {
                return executeCreate(title, text, summary);
            } catch (SessionExpiredException repeated) {
                throw new MediaWikiException(Kind.AUTHENTICATION_FAILED,
                        "MediaWiki session was rejected after re-authentication");
            }
        }
    }

    private MediaWikiEditResult executeCreate(String title, String text, String summary) {
        JsonNode tokenResponse = executeGet(Map.of(
                "action", "query", "meta", "tokens", "type", "csrf"),
                "edit token", Function.identity());
        String csrfToken = requiredText(tokenResponse.at("/query/tokens/csrftoken"), "CSRF token");

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("action", "edit");
        parameters.put("assert", "user");
        parameters.put("format", "json");
        parameters.put("formatversion", "2");
        parameters.put("title", title);
        parameters.put("text", text);
        parameters.put("summary", summary);
        parameters.put("createonly", "1");
        parameters.put("watchlist", "nochange");
        parameters.put("token", csrfToken);
        JsonNode root = sendPost(parameters, "page creation");
        rejectApiError(root);
        JsonNode edit = required(root, "/edit", "edit result");
        if (!"Success".equals(requiredText(edit.get("result"), "edit result"))) {
            throw invalid("MediaWiki did not confirm the page creation");
        }
        return new MediaWikiEditResult(requiredText(edit.get("title"), "created title"), 0,
                requiredLong(edit, "newrevid"));
    }

    @Override
    public MediaWikiCategoryInfo getCategoryInfo(String category) {
        requireValue(category, "category");
        String categoryTitle = category.contains(":") ? category : "Category:" + category;
        return authenticatedGet(Map.of("action", "query", "prop", "categoryinfo", "titles", categoryTitle),
                "category info", root -> {
            JsonNode page = firstPage(root);
            boolean exists = !page.path("missing").asBoolean(false);
            int count = exists ? page.path("categoryinfo").path("pages").asInt(0) : 0;
            return new MediaWikiCategoryInfo(requiredText(page.get("title"), "category title"), exists, count);
        });
    }

    private <T> T authenticatedGet(Map<String, String> parameters, String operation, Function<JsonNode, T> parser) {
        ensureAuthenticated();
        try {
            return executeGet(parameters, operation, parser);
        } catch (SessionExpiredException expired) {
            log.info("MediaWiki session expired during {}; authenticating once", operation);
            authenticate();
            try {
                return executeGet(parameters, operation, parser);
            } catch (SessionExpiredException repeated) {
                throw new MediaWikiException(Kind.AUTHENTICATION_FAILED,
                        "MediaWiki session was rejected after re-authentication");
            }
        }
    }

    private <T> T executeGet(Map<String, String> parameters, String operation, Function<JsonNode, T> parser) {
        Map<String, String> request = new LinkedHashMap<>(parameters);
        request.put("assert", "user");
        request.put("format", "json");
        request.put("formatversion", "2");
        JsonNode root = sendGet(request, operation);
        rejectApiError(root);
        try {
            return parser.apply(root);
        } catch (MediaWikiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MediaWikiException(Kind.INVALID_RESPONSE,
                    "MediaWiki returned an invalid response for " + operation, exception);
        }
    }

    private byte[] authenticatedDownload(URI uri, String operation) {
        ensureAuthenticated();
        try {
            return sendBinary(uri, operation);
        } catch (SessionExpiredException expired) {
            log.info("MediaWiki session expired during {}; authenticating once", operation);
            authenticate();
            try {
                return sendBinary(uri, operation);
            } catch (SessionExpiredException repeated) {
                throw new MediaWikiException(Kind.AUTHENTICATION_FAILED,
                        "MediaWiki session was rejected after re-authentication");
            }
        }
    }

    private byte[] sendBinary(URI uri, String operation) {
        if (uri == null || (!("https".equalsIgnoreCase(uri.getScheme())) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || !uri.getHost().equalsIgnoreCase(configuration.apiUrl().getHost())) {
            throw invalid("MediaWiki returned an untrusted image URL");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(configuration.requestTimeout()).GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            log.debug("MediaWiki {} returned HTTP {}", operation, response.statusCode());
            if (response.statusCode() == 401) {
                throw new SessionExpiredException();
            }
            if (response.statusCode() == 403) {
                throw new MediaWikiException(Kind.PERMISSION_DENIED, "MediaWiki denied " + operation);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MediaWikiException(Kind.UNREACHABLE,
                        "MediaWiki returned HTTP " + response.statusCode() + " for " + operation);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                throw new MediaWikiException(Kind.PERMISSION_DENIED,
                        "MediaWiki did not expose " + operation + " as an image");
            }
            byte[] content = response.body();
            if (content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw invalid("MediaWiki image is empty or exceeds the 25 MB import limit");
            }
            return content;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaWikiException(Kind.UNREACHABLE, "MediaWiki image request was interrupted", exception);
        } catch (IOException exception) {
            throw new MediaWikiException(Kind.UNREACHABLE, "MediaWiki image is not reachable", exception);
        }
    }

    private void ensureAuthenticated() {
        if (!authenticated) {
            authenticate();
        }
    }

    private JsonNode sendGet(Map<String, String> parameters, String operation) {
        String separator = configuration.apiUrl().toString().contains("?") ? "&" : "?";
        URI uri = URI.create(configuration.apiUrl() + separator + form(parameters));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(configuration.requestTimeout()).GET().build();
        return send(request, operation);
    }

    private JsonNode sendPost(Map<String, String> parameters, String operation) {
        HttpRequest request = HttpRequest.newBuilder(configuration.apiUrl())
                .timeout(configuration.requestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(parameters)))
                .build();
        return send(request, operation);
    }

    private JsonNode send(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("MediaWiki {} returned HTTP {}", operation, response.statusCode());
            if (response.statusCode() == 401) {
                throw new SessionExpiredException();
            }
            if (response.statusCode() == 403) {
                throw new MediaWikiException(Kind.PERMISSION_DENIED, "MediaWiki denied " + operation);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MediaWikiException(Kind.UNREACHABLE,
                        "MediaWiki returned HTTP " + response.statusCode() + " for " + operation);
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaWikiException(Kind.UNREACHABLE, "MediaWiki request was interrupted", exception);
        } catch (IOException exception) {
            throw new MediaWikiException(Kind.UNREACHABLE, "MediaWiki is not reachable", exception);
        }
    }

    private static void rejectApiError(JsonNode root) {
        JsonNode error = root.path("error");
        if (error.isMissingNode()) {
            return;
        }
        String code = error.path("code").asText("unknown");
        String info = text(error, "info");
        if ("assertuserfailed".equals(code) || "notloggedin".equals(code)) {
            throw new SessionExpiredException();
        }
        if ("badtoken".equals(code)) {
            throw new SessionExpiredException();
        }
        if ("editconflict".equals(code)) {
            throw new MediaWikiException(Kind.EDIT_CONFLICT,
                    "The Wiki page changed while the edit was being prepared");
        }
        if ("articleexists".equals(code)) {
            throw new MediaWikiException(Kind.EDIT_CONFLICT,
                    "A page with this title already exists in the Wiki");
        }
        if ("readapidenied".equals(code) || "permissiondenied".equals(code)
                || "protectedpage".equals(code) || "noedit".equals(code)
                || "blocked".equals(code)) {
            throw new MediaWikiException(Kind.PERMISSION_DENIED, "MediaWiki denied the requested operation");
        }
        if (("badvalue".equals(code) || "unknown_action".equals(code))
                && info != null && info.contains("afterimageimage")) {
            throw new MediaWikiException(Kind.API_ERROR,
                    "The MediaWiki extension AfterimageMediaApi is not installed or not enabled");
        }
        String detail = info == null || info.isBlank() ? "" : " (" + info.replaceAll("\\s+", " ").trim() + ")";
        throw new MediaWikiException(Kind.API_ERROR, "MediaWiki API error: " + code + detail);
    }

    private void requireConfiguration() {
        if (!configuration.credentialsConfigured()) {
            throw new MediaWikiException(Kind.NOT_CONFIGURED,
                    "MediaWiki credentials are not configured");
        }
    }

    private static JsonNode firstPage(JsonNode root) {
        JsonNode pages = required(root, "/query/pages", "pages");
        if (!pages.isArray() || pages.isEmpty()) {
            throw invalid("MediaWiki response contains no page");
        }
        return pages.get(0);
    }

    private static MediaWikiArticle mapArticle(JsonNode page) {
        JsonNode revision = required(page, "/revisions/0", "page revision");
        JsonNode content = required(revision, "/slots/main/content", "page content");
        return new MediaWikiArticle(requiredLong(page, "pageid"), requiredInt(page, "ns"),
                requiredText(page.get("title"), "title"), requiredLong(revision, "revid"),
                requiredInstant(revision, "timestamp"), content.asText());
    }

    private static Optional<ImageInfo> mapImageInfo(JsonNode root, String requestedTitle) {
        JsonNode page = firstPage(root);
        if (page.path("missing").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode info = page.at("/imageinfo/0");
        if (info.isMissingNode()) {
            return Optional.empty();
        }
        String title = requiredText(page.get("title"), "image title");
        String filename = title.contains(":") ? title.substring(title.indexOf(':') + 1) : requestedTitle;
        URI sourceUrl = URI.create(requiredText(info.get("url"), "image source URL"));
        String thumbnailUrl = text(info, "thumburl");
        boolean hasThumbnail = thumbnailUrl != null && !thumbnailUrl.isBlank();
        URI downloadUrl = URI.create(hasThumbnail ? thumbnailUrl : sourceUrl.toString());
        Integer width = optionalInt(info, hasThumbnail ? "thumbwidth" : "width");
        Integer height = optionalInt(info, hasThumbnail ? "thumbheight" : "height");
        return Optional.of(new ImageInfo(title, filename, sourceUrl, downloadUrl,
                requiredText(info.get("mime"), "image MIME type"), width, height));
    }

    private static Map<String, String> continuation(JsonNode root) {
        JsonNode next = root.path("continue");
        if (!next.isObject()) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        next.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }

    private static JsonNode required(JsonNode root, String pointer, String label) {
        JsonNode value = root.at(pointer);
        if (value.isMissingNode() || value.isNull()) {
            throw invalid("MediaWiki response is missing " + label);
        }
        return value;
    }

    private static String requiredText(JsonNode value, String label) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("MediaWiki response is missing " + label);
        }
        return value.asText();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) throw invalid("MediaWiki response is missing " + field);
        return value.longValue();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) throw invalid("MediaWiki response is missing " + field);
        return value.intValue();
    }

    private static Integer optionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.intValue();
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        String value = requiredText(node.get(field), field);
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("MediaWiki response contains an invalid " + field);
        }
    }

    private static Instant optionalInstant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : Instant.parse(value.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static String form(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static MediaWikiException invalid(String message) {
        return new MediaWikiException(Kind.INVALID_RESPONSE, message);
    }

    private static final class SessionExpiredException extends RuntimeException {}
    private record ImageInfo(String title, String filename, URI sourceUrl, URI downloadUrl,
                             String mimeType, Integer width, Integer height) {}
    private record ImagePayload(String filename, String mimeType, Integer width, Integer height, byte[] content) {}
}
