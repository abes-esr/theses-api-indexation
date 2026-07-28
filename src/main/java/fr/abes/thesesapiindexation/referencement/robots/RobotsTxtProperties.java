package fr.abes.thesesapiindexation.referencement.robots;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "referencement.robots")
public record RobotsTxtProperties(
        URI url,
        Duration connectTimeout,
        Duration readTimeout
) {
}
