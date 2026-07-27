package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static class CapturingReferencementIndexGateway implements ReferencementIndexGateway {

        private String createdIndex;
        private String createdMapping;

        @Override
        public boolean exists(String indexName) {
            return false;
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
    }
}
