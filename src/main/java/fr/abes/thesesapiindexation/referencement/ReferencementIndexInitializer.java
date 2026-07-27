package fr.abes.thesesapiindexation.referencement;

import java.io.IOException;
import java.io.InputStream;

public class ReferencementIndexInitializer {

    private final ReferencementIndexGateway gateway;
    private final ReferencementIndexProperties properties;

    public ReferencementIndexInitializer(
            ReferencementIndexGateway gateway,
            ReferencementIndexProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public void initialize() {
        if (gateway.exists(properties.name())) {
            return;
        }

        try (InputStream mapping = properties.mapping().getInputStream()) {
            gateway.create(properties.name(), mapping);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de lire le mapping de l'index " + properties.name(),
                    exception
            );
        }
    }
}
