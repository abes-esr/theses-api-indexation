package fr.abes.thesesapiindexation.referencement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencementMappingTest {

    private static final String MAPPING_PATH = "/indexs/referencement.json";

    @Test
    void leMappingDefinitLeSchemaStrictDuReferencement() throws Exception {
        try (InputStream mappingStream = getClass().getResourceAsStream(MAPPING_PATH)) {
            assertThat(mappingStream)
                    .as("le mapping %s doit être présent dans le classpath", MAPPING_PATH)
                    .isNotNull();

            JsonNode mapping = new ObjectMapper().readTree(mappingStream);

            assertThat(mapping.at("/mappings/dynamic").asText()).isEqualTo("strict");
            assertThat(mapping.at("/mappings/properties/pageType/type").asText()).isEqualTo("keyword");
            assertThat(mapping.at("/mappings/properties/noIndex/type").asText()).isEqualTo("boolean");
            assertThat(mapping.at("/mappings/properties/demandeRef/type").asText()).isEqualTo("keyword");
            assertThat(mapping.at("/mappings/properties/demandeRef/ignore_above").asInt()).isEqualTo(64);
            assertThat(mapping.at("/mappings/properties/updatedBy/type").asText()).isEqualTo("keyword");
            assertThat(mapping.at("/mappings/properties/updatedBy/ignore_above").asInt()).isEqualTo(255);
            assertThat(mapping.at("/mappings/properties/updatedAt/type").asText()).isEqualTo("date");
        }
    }
}
