package de.afterimage.piwigo.infrastructure;

import com.sun.net.httpserver.HttpServer;
import de.afterimage.config.AfterimageProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PiwigoApiClientTest {
    @Test
    void sendsPiwigo16ApiKeyHeaderWithoutSessionLogin() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/ws.php", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("X-PIWIGO-API"));
            query.set(exchange.getRequestURI().getQuery());
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBody = "{\"stat\":\"ok\",\"result\":{\"categories\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            URI api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ws.php");
            var configuration = new AfterimageProperties.Piwigo(true, api.resolve("/"), api,
                    "pkid-afterimage:secret", null, null, 50, Duration.ofSeconds(2), Duration.ofSeconds(2));
            new PiwigoApiClient(configuration, new ObjectMapper()).albums();
            assertThat(header.get()).isEqualTo("pkid-afterimage:secret");
            assertThat(method.get()).isEqualTo("POST");
            assertThat(query.get()).isEqualTo("format=json");
            assertThat(body.get()).contains("method=pwg.categories.getList").doesNotContain("pwg.session.login");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsAlbumIdAsArrayWhenLoadingAlbumImages() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/ws.php", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBody = "{\"stat\":\"ok\",\"result\":{\"paging\":{\"total_count\":0},\"images\":[]}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            URI api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ws.php");
            var configuration = new AfterimageProperties.Piwigo(true, api.resolve("/"), api,
                    "pkid-afterimage:secret", null, null, 50, Duration.ofSeconds(2), Duration.ofSeconds(2));

            new PiwigoApiClient(configuration, new ObjectMapper()).images(96, 0, 12);

            assertThat(query.get()).isEqualTo("format=json");
            assertThat(body.get()).contains("method=pwg.categories.getImages")
                    .contains("cat_id%5B%5D=96")
                    .contains("recursive=true")
                    .doesNotContain("cat_id=96");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requestsRandomImagesFromTheSelectedAlbumTree() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/ws.php", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBody = "{\"stat\":\"ok\",\"result\":{\"paging\":{\"total_count\":419},\"images\":[]}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            URI api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ws.php");
            var configuration = new AfterimageProperties.Piwigo(true, api.resolve("/"), api,
                    "pkid-afterimage:secret", null, null, 50, Duration.ofSeconds(2), Duration.ofSeconds(2));

            new PiwigoApiClient(configuration, new ObjectMapper()).randomImages(1, 12);

            assertThat(body.get()).contains("cat_id%5B%5D=1")
                    .contains("recursive=true")
                    .contains("per_page=12")
                    .contains("order=random");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assignsTheMostSpecificChildAlbumToEachImage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ws.php", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = body.contains("pwg.categories.getList")
                    ? "{\"stat\":\"ok\",\"result\":{\"categories\":["
                    + "{\"id\":10,\"name\":\"Redestruction\",\"nb_images\":500},"
                    + "{\"id\":11,\"name\":\"27.12.2025 Abschiedskonzert\",\"id_uppercat\":10,\"nb_images\":120}]}}"
                    : "{\"stat\":\"ok\",\"result\":{\"paging\":{\"total_count\":1},\"images\":[{"
                    + "\"id\":99,\"file\":\"DSC_0099.jpg\",\"element_url\":\"/upload/DSC_0099.jpg\","
                    + "\"categories\":[{\"id\":10,\"page_url\":\"/picture/99/root\"},"
                    + "{\"id\":11,\"page_url\":\"/picture/99/concert\"}]}]}}";
            byte[] responseBody = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            URI api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ws.php");
            var configuration = new AfterimageProperties.Piwigo(true, api.resolve("/"), api,
                    "pkid-afterimage:secret", null, null, 50, Duration.ofSeconds(2), Duration.ofSeconds(2));

            var image = new PiwigoApiClient(configuration, new ObjectMapper()).images(10, 0, 12).images().getFirst();

            assertThat(image.albumName()).isEqualTo("27.12.2025 Abschiedskonzert");
            assertThat(image.pageUrl()).endsWith("/picture/99/concert");
        } finally {
            server.stop(0);
        }
    }
}
