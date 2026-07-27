package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferencementIndexInitializerTest {

    @Test
    void creeLIndexReferencementAvecLeMappingVersionneQuandIlEstAbsent() {
        CapturingReferencementIndexGateway gateway = new CapturingReferencementIndexGateway();
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                "referencement",
                new ClassPathResource("indexs/referencement.json")
        );
        ReferencementIndexInitializer initializer = new ReferencementIndexInitializer(gateway, properties);

        initializer.initialize();

        assertThat(gateway.createdIndex).isEqualTo("referencement");
        assertThat(gateway.createdMapping)
                .contains("\"dynamic\": \"strict\"")
                .contains("\"noIndex\"");
    }

    @Test
    void refuseUnIndexExistantDontNoIndexNestPasUnBooleen() {
        CapturingReferencementIndexGateway gateway = new CapturingReferencementIndexGateway();
        gateway.indexExists = true;
        gateway.existingMapping = """
                {
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "pageType": { "type": "keyword" },
                      "noIndex": { "type": "keyword" },
                      "demandeRef": { "type": "keyword" },
                      "updatedBy": { "type": "keyword" },
                      "updatedAt": { "type": "date" }
                    }
                  }
                }
                """;
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                "referencement",
                new ClassPathResource("indexs/referencement.json")
        );
        ReferencementIndexInitializer initializer = new ReferencementIndexInitializer(gateway, properties);

        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noIndex")
                .hasMessageContaining("boolean")
                .hasMessageContaining("keyword");
    }

    @Test
    void refuseUnIndexExistantSansLeChampUpdatedAt() {
        CapturingReferencementIndexGateway gateway = new CapturingReferencementIndexGateway();
        gateway.indexExists = true;
        gateway.existingMapping = """
                {
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "pageType": { "type": "keyword" },
                      "noIndex": { "type": "boolean" },
                      "demandeRef": { "type": "keyword" },
                      "updatedBy": { "type": "keyword" }
                    }
                  }
                }
                """;
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                "referencement",
                new ClassPathResource("indexs/referencement.json")
        );
        ReferencementIndexInitializer initializer = new ReferencementIndexInitializer(gateway, properties);

        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("updatedAt")
                .hasMessageContaining("date")
                .hasMessageContaining("absent");
    }

    @Test
    void refuseUnIndexExistantQuiAutoriseLesChampsDynamiques() {
        CapturingReferencementIndexGateway gateway = new CapturingReferencementIndexGateway();
        gateway.indexExists = true;
        gateway.existingMapping = """
                {
                  "mappings": {
                    "dynamic": "true",
                    "properties": {
                      "pageType": { "type": "keyword" },
                      "noIndex": { "type": "boolean" },
                      "demandeRef": { "type": "keyword" },
                      "updatedBy": { "type": "keyword" },
                      "updatedAt": { "type": "date" }
                    }
                  }
                }
                """;
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                "referencement",
                new ClassPathResource("indexs/referencement.json")
        );
        ReferencementIndexInitializer initializer = new ReferencementIndexInitializer(gateway, properties);

        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dynamic")
                .hasMessageContaining("strict")
                .hasMessageContaining("true");
    }

    @Test
    void conserveUnIndexExistantDontLeMappingEstCompatible() {
        CapturingReferencementIndexGateway gateway = new CapturingReferencementIndexGateway();
        gateway.indexExists = true;
        gateway.existingMapping = """
                {
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "pageType": { "type": "keyword" },
                      "noIndex": { "type": "boolean" },
                      "demandeRef": { "type": "keyword" },
                      "updatedBy": { "type": "keyword" },
                      "updatedAt": { "type": "date" }
                    }
                  }
                }
                """;
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                "referencement",
                new ClassPathResource("indexs/referencement.json")
        );
        ReferencementIndexInitializer initializer = new ReferencementIndexInitializer(gateway, properties);

        initializer.initialize();

        assertThat(gateway.createdIndex).isNull();
    }

    private static class CapturingReferencementIndexGateway implements ReferencementIndexGateway {

        private boolean indexExists;
        private String existingMapping;
        private String createdIndex;
        private String createdMapping;

        @Override
        public boolean exists(String indexName) {
            return indexExists;
        }

        @Override
        public void create(String indexName, InputStream mapping) {
            createdIndex = indexName;
            try {
                createdMapping = new String(mapping.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Impossible de lire le mapping capturé par le test", exception);
            }
        }

        @Override
        public InputStream mapping(String indexName) {
            return new ByteArrayInputStream(existingMapping.getBytes(StandardCharsets.UTF_8));
        }
    }
}
