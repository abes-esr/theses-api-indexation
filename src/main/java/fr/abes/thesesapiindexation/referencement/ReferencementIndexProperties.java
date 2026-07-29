package fr.abes.thesesapiindexation.referencement;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "referencement.index")
public record ReferencementIndexProperties(String name, Resource mapping) {
}
