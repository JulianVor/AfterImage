package de.afterimage.wiki.infrastructure;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.wiki.application.MediaWikiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaWikiApiClientTest {
    private HttpServer server;
    private URI apiUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        apiUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/w/api.php");
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void authenticatesSuccessfullyAndKeepsTheSessionCookie() {
        AtomicInteger logins = new AtomicInteger();
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"TOKEN+\\\\\"}}}");
            } else {
                logins.incrementAndGet();
                assertThat(values.get("lgname")).isEqualTo("test-user@test-bot");
                assertThat(values.get("lgpassword")).isEqualTo("test-secret");
                exchange.getResponseHeaders().add("Set-Cookie", "session=valid; Path=/; HttpOnly");
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            }
        });

        client().authenticate();

        assertThat(logins).hasValue(1);
    }

    @Test
    void reportsFailedLoginWithoutExposingThePassword() {
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            respond(exchange, 200, "tokens".equals(values.get("meta"))
                    ? "{\"query\":{\"tokens\":{\"logintoken\":\"TOKEN\"}}}"
                    : "{\"login\":{\"result\":\"Failed\"}}");
        });

        assertThatThrownBy(() -> client().authenticate())
                .isInstanceOfSatisfying(MediaWikiException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(MediaWikiException.Kind.AUTHENTICATION_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("test-secret");
                });
    }

    @Test
    void readsAndMapsAPage() {
        standardLoginThen((exchange, values) -> respond(exchange, 200, """
                {"query":{"pages":[{"pageid":42,"ns":0,"title":"Redestruction","revisions":[
                  {"revid":9,"timestamp":"2026-09-04T12:00:00Z","slots":{"main":{"content":"Wiki text"}}}
                ]}]}}
                """));

        var page = client().getPage("Redestruction").orElseThrow();

        assertThat(page.pageId()).isEqualTo(42);
        assertThat(page.revisionId()).isEqualTo(9);
        assertThat(page.wikitext()).isEqualTo("Wiki text");
    }

    @Test
    void downloadsARestrictedWikiThumbnailWithImageMetadata() {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        server.createContext("/images/First.jpg", exchange -> respond(exchange, 200, "image/jpeg", imageBytes));
        standardLoginThen((exchange, values) -> {
            assertThat(values.get("prop")).isEqualTo("imageinfo");
            assertThat(values.get("titles")).isEqualTo("File:First.jpg");
            assertThat(values.get("iiurlwidth")).isEqualTo("2000");
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            respond(exchange, 200, """
                    {"query":{"pages":[{"pageid":8,"ns":6,"title":"File:First.jpg","imageinfo":[{
                      "url":"%s/images/First.jpg","mime":"image/jpeg","width":3000,"height":2000,
                      "thumburl":"%s/images/First.jpg","thumbwidth":2000,"thumbheight":1333
                    }]}]}}
                    """.formatted(origin, origin));
        });

        var image = client().getImage("File:First.jpg", 2000).orElseThrow();

        assertThat(image.filename()).isEqualTo("First.jpg");
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
        assertThat(image.width()).isEqualTo(2000);
        assertThat(image.height()).isEqualTo(1333);
        assertThat(image.content()).containsExactly(imageBytes);
    }

    @Test
    void fallsBackToTheAuthenticatedImageApiWhenPrivateFileDeliveryRejectsBotSessions() {
        server.createContext("/images/Private.jpg",
                exchange -> respond(exchange, 403, "text/html", "denied".getBytes(StandardCharsets.UTF_8)));
        standardLoginThen((exchange, values) -> {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            if ("afterimageimage".equals(values.get("action"))) {
                assertThat(values.get("title")).isEqualTo("File:Private.jpg");
                respond(exchange, 200, """
                        {"afterimageimage":{"filename":"Private.jpg","mime":"image/jpeg",
                          "width":1600,"height":900,"content":"AQID"}}
                        """);
            } else {
                respond(exchange, 200, """
                        {"query":{"pages":[{"pageid":9,"ns":6,"title":"File:Private.jpg","imageinfo":[{
                          "url":"%s/images/Private.jpg","mime":"image/jpeg","width":1600,"height":900
                        }]}]}}
                        """.formatted(origin));
            }
        });

        var image = client().getImage("File:Private.jpg", 2000).orElseThrow();

        assertThat(image.filename()).isEqualTo("Private.jpg");
        assertThat(image.content()).containsExactly(1, 2, 3);
    }

    @Test
    void explainsWhenThePrivateImageApiExtensionIsNotEnabled() {
        server.createContext("/images/Private.jpg",
                exchange -> respond(exchange, 403, "text/html", "denied".getBytes(StandardCharsets.UTF_8)));
        standardLoginThen((exchange, values) -> {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            if ("afterimageimage".equals(values.get("action"))) {
                respond(exchange, 200, """
                        {"error":{"code":"badvalue",
                          "info":"Unrecognized value for parameter \\"action\\": afterimageimage."}}
                        """);
            } else {
                respond(exchange, 200, """
                        {"query":{"pages":[{"pageid":9,"ns":6,"title":"File:Private.jpg","imageinfo":[{
                          "url":"%s/images/Private.jpg","mime":"image/jpeg","width":1600,"height":900
                        }]}]}}
                        """.formatted(origin));
            }
        });

        assertThatThrownBy(() -> client().getImage("File:Private.jpg", 2000))
                .isInstanceOfSatisfying(MediaWikiException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(MediaWikiException.Kind.API_ERROR);
                    assertThat(exception.getMessage()).contains("AfterimageMediaApi", "not installed or not enabled");
                });
    }

    @Test
    void distinguishesAMissingPage() {
        standardLoginThen((exchange, values) ->
                respond(exchange, 200, "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Absent\",\"missing\":true}]}}"));

        assertThat(client().getPage("Absent")).isEmpty();
    }

    @Test
    void distinguishesPermissionDenied() {
        standardLoginThen((exchange, values) ->
                respond(exchange, 200, "{\"error\":{\"code\":\"readapidenied\",\"info\":\"denied\"}}"));

        assertThatThrownBy(() -> client().getPage("Private"))
                .isInstanceOfSatisfying(MediaWikiException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(MediaWikiException.Kind.PERMISSION_DENIED));
    }

    @Test
    void authenticatesExactlyOnceAgainAfterAnExpiredSession() {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger pageRequests = new AtomicInteger();
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"TOKEN\"}}}");
            } else if ("login".equals(values.get("action"))) {
                logins.incrementAndGet();
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            } else if (pageRequests.incrementAndGet() == 1) {
                respond(exchange, 200, "{\"error\":{\"code\":\"assertuserfailed\"}}");
            } else {
                respond(exchange, 200, "{\"query\":{\"pages\":[{\"pageid\":1,\"ns\":0,\"title\":\"Page\",\"revisions\":[{\"revid\":2,\"timestamp\":\"2026-09-04T12:00:00Z\",\"slots\":{\"main\":{\"content\":\"ok\"}}}]}]}}");
            }
        });

        assertThat(client().getPage("Page")).isPresent();
        assertThat(logins).hasValue(2);
        assertThat(pageRequests).hasValue(2);
    }

    @Test
    void readsAllCategoryPagesAcrossContinuationBatches() {
        AtomicInteger categoryRequests = new AtomicInteger();
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"TOKEN\"}}}");
            } else if ("login".equals(values.get("action"))) {
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            } else if (categoryRequests.incrementAndGet() == 1) {
                assertThat(values.get("gcmtitle")).isEqualTo("Category:Afterimage");
                respond(exchange, 200, "{\"continue\":{\"continue\":\"gcmcontinue||\",\"gcmcontinue\":\"next\"},\"query\":{\"pages\":[" + pageJson(2, "Beta") + "]}}");
            } else {
                assertThat(values.get("gcmcontinue")).isEqualTo("next");
                assertThat(values.get("continue")).isEqualTo("gcmcontinue||");
                respond(exchange, 200, "{\"query\":{\"pages\":[" + pageJson(1, "Alpha") + "]}}");
            }
        });

        var pages = client().getPages("Afterimage", 10);

        assertThat(pages).extracting("title").containsExactly("Alpha", "Beta");
        assertThat(categoryRequests).hasValue(2);
    }

    @Test
    void readsCategoryExistenceAndPageCount() {
        standardLoginThen((exchange, values) -> respond(exchange, 200,
                "{\"query\":{\"pages\":[{\"pageid\":7,\"ns\":14,\"title\":\"Category:Afterimage\",\"categoryinfo\":{\"size\":5,\"pages\":4,\"files\":1,\"subcats\":0}}]}}"));

        var category = client().getCategoryInfo("Afterimage");

        assertThat(category.exists()).isTrue();
        assertThat(category.pageCount()).isEqualTo(4);
    }

    @Test
    void readsMainNamespaceWithoutARequiredCategory() {
        standardLoginThen((exchange, values) -> {
            assertThat(values.get("generator")).isEqualTo("allpages");
            assertThat(values.get("gapnamespace")).isEqualTo("0");
            respond(exchange, 200, "{\"query\":{\"pages\":[" + pageJson(1, "Alpha") + "]}}");
        });

        assertThat(client().getPages(null, 10)).extracting("title").containsExactly("Alpha");
    }

    @Test
    void countsReadableMainNamespacePages() {
        standardLoginThen((exchange, values) -> {
            assertThat(values.get("list")).isEqualTo("allpages");
            respond(exchange, 200, "{\"query\":{\"allpages\":[{\"pageid\":1},{\"pageid\":2}]}}");
        });

        assertThat(client().getMainNamespacePageCount(10)).isEqualTo(2);
    }

    @Test
    void editsWithCsrfTokenAndRevisionConflictProtection() {
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta")) && "login".equals(values.get("type"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"LOGIN\"}}}");
            } else if ("login".equals(values.get("action"))) {
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            } else if ("tokens".equals(values.get("meta")) && "csrf".equals(values.get("type"))) {
                assertThat(values.get("assert")).isEqualTo("user");
                respond(exchange, 200, "{\"curtimestamp\":\"2026-09-04T12:01:00Z\",\"query\":{\"tokens\":{\"csrftoken\":\"CSRF-TOKEN\"}}}");
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(values.get("action")).isEqualTo("edit");
                assertThat(values.get("assert")).isEqualTo("user");
                assertThat(values.get("title")).isEqualTo("Example");
                assertThat(values.get("text")).contains("Media URL=https://video.example/123");
                assertThat(values.get("nocreate")).isEqualTo("1");
                assertThat(values.get("baserevid")).isEqualTo("41");
                assertThat(values.get("basetimestamp")).isEqualTo("2026-09-04T12:00:00Z");
                assertThat(values.get("starttimestamp")).isEqualTo("2026-09-04T12:01:00Z");
                assertThat(values.get("token")).isEqualTo("CSRF-TOKEN");
                respond(exchange, 200, "{\"edit\":{\"result\":\"Success\",\"title\":\"Example\",\"oldrevid\":41,\"newrevid\":42}}");
            }
        });

        var result = client().editPage("Example", "Media URL=https://video.example/123", 41,
                Instant.parse("2026-09-04T12:00:00Z"), "Add Media URL");

        assertThat(result.previousRevisionId()).isEqualTo(41);
        assertThat(result.newRevisionId()).isEqualTo(42);
    }

    @Test
    void reportsEditConflictsWithoutOverwriting() {
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta")) && "login".equals(values.get("type"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"LOGIN\"}}}");
            } else if ("login".equals(values.get("action"))) {
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            } else if ("tokens".equals(values.get("meta"))) {
                respond(exchange, 200, "{\"curtimestamp\":\"2026-09-04T12:01:00Z\",\"query\":{\"tokens\":{\"csrftoken\":\"CSRF\"}}}");
            } else {
                respond(exchange, 200, "{\"error\":{\"code\":\"editconflict\",\"info\":\"conflict\"}}");
            }
        });

        assertThatThrownBy(() -> client().editPage("Example", "updated", 41,
                Instant.parse("2026-09-04T12:00:00Z"), "Update"))
                .isInstanceOfSatisfying(MediaWikiException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(MediaWikiException.Kind.EDIT_CONFLICT));
    }

    private MediaWikiApiClient client() {
        var configuration = new AfterimageProperties.Wiki(apiUrl.resolve("/"), apiUrl,
                "test-user@test-bot", "test-secret", "Afterimage", true,
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new MediaWikiApiClient(configuration, new ObjectMapper());
    }

    private void standardLoginThen(ExchangeHandler handler) {
        server.createContext("/w/api.php", exchange -> {
            Map<String, String> values = parameters(exchange);
            if ("tokens".equals(values.get("meta"))) {
                respond(exchange, 200, "{\"query\":{\"tokens\":{\"logintoken\":\"TOKEN\"}}}");
            } else if ("login".equals(values.get("action"))) {
                respond(exchange, 200, "{\"login\":{\"result\":\"Success\"}}");
            } else {
                handler.handle(exchange, values);
            }
        });
    }

    private static Map<String, String> parameters(HttpExchange exchange) throws IOException {
        String raw = exchange.getRequestURI().getRawQuery();
        if ("POST".equals(exchange.getRequestMethod())) {
            raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> result = new HashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String pair : raw.split("&")) {
                String[] parts = pair.split("=", 2);
                result.put(decode(parts[0]), decode(parts.length == 2 ? parts[1] : ""));
            }
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String pageJson(long id, String title) {
        return "{\"pageid\":" + id + ",\"ns\":0,\"title\":\"" + title
                + "\",\"revisions\":[{\"revid\":" + (id + 10)
                + ",\"timestamp\":\"2026-09-04T12:00:00Z\",\"slots\":{\"main\":{\"content\":\"text\"}}}]}";
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange, Map<String, String> values) throws IOException;
    }
}
