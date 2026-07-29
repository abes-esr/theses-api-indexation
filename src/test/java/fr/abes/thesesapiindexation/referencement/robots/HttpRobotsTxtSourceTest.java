package fr.abes.thesesapiindexation.referencement.robots;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRobotsTxtSourceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void telechargeIntegralementUneReponseDeuxCents()
            throws IOException {
        String body = "User-agent: *\nDisallow: /270350292";
        server = server(200, body);
        HttpRobotsTxtSource source = source();

        assertThat(source.download()).isEqualTo(body);
    }

    @Test
    void refuseUnStatutHttpNonValide() throws IOException {
        server = server(503, "indisponible");

        assertThatThrownBy(() -> source().download())
                .isInstanceOf(RobotsTxtSourceException.class)
                .hasMessageContaining("503");
    }

    @Test
    void echoueQuandLeDelaiDeLectureEstDepasse()
            throws IOException {
        server = server(
                200,
                "Disallow: /270350292",
                Duration.ofMillis(250)
        );

        assertThatThrownBy(
                () -> source(Duration.ofMillis(50)).download()
        )
                .isInstanceOf(RobotsTxtSourceException.class)
                .hasMessageContaining("télécharger");
    }

    private HttpServer server(int status, String body)
            throws IOException {
        return server(status, body, Duration.ZERO);
    }

    private HttpServer server(
            int status,
            String body,
            Duration delay
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        httpServer.createContext("/robots.txt", exchange -> {
            try {
                Thread.sleep(delay.toMillis());
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer;
    }

    private HttpRobotsTxtSource source() {
        return source(Duration.ofSeconds(1));
    }

    private HttpRobotsTxtSource source(Duration readTimeout) {
        URI url = URI.create(
                "http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/robots.txt"
        );
        RobotsTxtProperties properties = new RobotsTxtProperties(
                url,
                Duration.ofSeconds(1),
                readTimeout
        );
        return new HttpRobotsTxtSource(
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .build(),
                properties
        );
    }
}
