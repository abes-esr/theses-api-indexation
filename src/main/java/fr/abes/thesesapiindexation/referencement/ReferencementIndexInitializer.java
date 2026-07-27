package fr.abes.thesesapiindexation.referencement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class ReferencementIndexInitializer {

    private final ReferencementIndexGateway gateway;
    private final ReferencementIndexProperties properties;
    private final ObjectMapper objectMapper;

    public ReferencementIndexInitializer(
            ReferencementIndexGateway gateway,
            ReferencementIndexProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public void initialize() {
        if (gateway.exists(properties.name())) {
            validateExistingMapping();
            return;
        }

        try (InputStream mapping = properties.mapping().getInputStream()) {
            try {
                gateway.create(properties.name(), mapping);
            } catch (ReferencementIndexAlreadyExistsException exception) {
                validateExistingMapping();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de lire le mapping de l'index " + properties.name(),
                    exception
            );
        }
    }

    private void validateExistingMapping() {
        try (
                InputStream expectedMappingStream = properties.mapping().getInputStream();
                InputStream existingMappingStream = gateway.mapping(properties.name())
        ) {
            JsonNode expectedMapping = objectMapper.readTree(expectedMappingStream);
            JsonNode existingMapping = objectMapper.readTree(existingMappingStream);

            validateDynamicMode(expectedMapping, existingMapping);

            Iterator<String> expectedFieldNames = expectedMapping
                    .at("/mappings/properties")
                    .fieldNames();
            expectedFieldNames.forEachRemaining(
                    fieldName -> validateFieldType(expectedMapping, existingMapping, fieldName)
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de contrôler le mapping de l'index " + properties.name(),
                    exception
            );
        }
    }

    private void validateDynamicMode(JsonNode expectedMapping, JsonNode existingMapping) {
        String expectedDynamicMode = expectedMapping.at("/mappings/dynamic").asText();
        String existingDynamicMode = existingMapping.at("/mappings/dynamic").asText();

        if (!expectedDynamicMode.equals(existingDynamicMode)) {
            throw new IllegalStateException(
                    "Mapping incompatible pour dynamic de l'index " + properties.name()
                            + " : valeur attendue " + expectedDynamicMode
                            + ", valeur trouvée " + existingDynamicMode
            );
        }
    }

    private void validateFieldType(JsonNode expectedMapping, JsonNode existingMapping, String fieldName) {
        String fieldPath = "/mappings/properties/" + fieldName + "/type";
        String expectedType = expectedMapping.at(fieldPath).asText();
        JsonNode existingTypeNode = existingMapping.at(fieldPath);
        String existingType = existingTypeNode.isMissingNode()
                ? "absent"
                : existingTypeNode.asText();

        if (!expectedType.equals(existingType)) {
            throw new IllegalStateException(
                    "Mapping incompatible pour le champ " + fieldName
                            + " de l'index " + properties.name()
                            + " : type attendu " + expectedType
                            + ", type trouvé " + existingType
            );
        }
    }
}
