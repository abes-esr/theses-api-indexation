package fr.abes.thesesapiindexation.referencement;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "es")
public record ElasticsearchConnectionProperties(
        String hostname,
        int port,
        String protocol,
        String username,
        String password,
        Resource caCertificate
) {
}
