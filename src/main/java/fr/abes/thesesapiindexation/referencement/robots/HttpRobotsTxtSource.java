package fr.abes.thesesapiindexation.referencement.robots;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpRobotsTxtSource implements RobotsTxtSource {

    private final HttpClient client;
    private final RobotsTxtProperties properties;

    public HttpRobotsTxtSource(
            HttpClient client,
            RobotsTxtProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String download() {
        HttpRequest request = HttpRequest.newBuilder(properties.url())
                .timeout(properties.readTimeout())
                .header("Accept", "text/plain")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new RobotsTxtSourceException(
                        "Le téléchargement du robots.txt a retourné "
                                + "le statut "
                                + response.statusCode()
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw downloadFailure(exception);
        } catch (IOException exception) {
            throw downloadFailure(exception);
        }
    }

    private RobotsTxtSourceException downloadFailure(Exception cause) {
        return new RobotsTxtSourceException(
                "Impossible de télécharger le robots.txt depuis "
                        + properties.url(),
                cause
        );
    }
}
