package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementDocument;
import fr.abes.thesesapiindexation.referencement.ReferencementDocumentAccessException;
import fr.abes.thesesapiindexation.referencement.ReferencementDocumentGateway;
import fr.abes.thesesapiindexation.referencement.ReferencementPageType;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotsTxtImporterTest {

    private static final Instant NOW =
            Instant.parse("2026-07-28T12:00:00Z");

    @Test
    void creeLesDocumentsValidesEtRetourneLeBilan() {
        MemoryGateway gateway = new MemoryGateway();
        RobotsTxtImporter importer = importer(
                () -> String.join("\n",
                        "User-agent: *",
                        "Disallow: /2024AIXM0640",
                        "Disallow: /270350292",
                        "Disallow: /s233841",
                        "Disallow: /2024AIXM0640"
                ),
                gateway
        );

        RobotsTxtImportReport report = importer.importInitial();

        assertThat(report).isEqualTo(new RobotsTxtImportReport(
                5, 3, 1, 1, 0, 3, 0
        ));
        assertThat(gateway.documents.get("270350292")).isEqualTo(
                new ReferencementDocument(
                        ReferencementPageType.PERSONNE,
                        true,
                        "IMPORT-ROBOTS-INITIAL",
                        "robots.txt-importer",
                        NOW
                )
        );
        assertThat(gateway.documents).containsKeys(
                "2024AIXM0640",
                "270350292",
                "s233841"
        );
    }

    @Test
    void conserveUnDocumentExistant() {
        MemoryGateway gateway = new MemoryGateway();
        ReferencementDocument existing = new ReferencementDocument(
                ReferencementPageType.PERSONNE,
                false,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-27T08:00:00Z")
        );
        gateway.documents.put("270350292", existing);
        RobotsTxtImporter importer =
                importer(() -> "Disallow: /270350292", gateway);

        RobotsTxtImportReport report = importer.importInitial();

        assertThat(report.created()).isZero();
        assertThat(report.existing()).isEqualTo(1);
        assertThat(gateway.documents.get("270350292")).isEqualTo(existing);
    }

    @Test
    void exposeLeBilanPartielEtPermetLaRelance() {
        MemoryGateway gateway = new MemoryGateway();
        gateway.failOn = "270350292";
        RobotsTxtImporter importer = importer(
                () -> String.join("\n",
                        "Disallow: /2024AIXM0640",
                        "Disallow: /270350292",
                        "Disallow: /s233841"
                ),
                gateway
        );

        assertThatThrownBy(importer::importInitial)
                .isInstanceOfSatisfying(
                        RobotsTxtImportException.class,
                        exception -> {
                            assertThat(exception.failedIdentifier())
                                    .isEqualTo("270350292");
                            assertThat(exception.report().created())
                                    .isEqualTo(1);
                            assertThat(exception.report().existing())
                                    .isZero();
                        }
                );
        assertThat(gateway.documents)
                .containsKey("2024AIXM0640")
                .doesNotContainKeys("270350292", "s233841");

        gateway.failOn = null;
        RobotsTxtImportReport retry = importer.importInitial();

        assertThat(retry.created()).isEqualTo(2);
        assertThat(retry.existing()).isEqualTo(1);
        assertThat(gateway.documents).containsKeys(
                "2024AIXM0640",
                "270350292",
                "s233841"
        );
    }

    private RobotsTxtImporter importer(
            RobotsTxtSource source,
            MemoryGateway gateway
    ) {
        return new RobotsTxtImporter(
                source,
                new RobotsTxtParser(),
                new ReferencementWriteService(
                        gateway,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
    }

    private static final class MemoryGateway
            implements ReferencementDocumentGateway {
        private final Map<String, ReferencementDocument> documents =
                new LinkedHashMap<>();
        private String failOn;

        @Override
        public Optional<ReferencementDocument> findById(String id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public void save(String id, ReferencementDocument document) {
            documents.put(id, document);
        }

        @Override
        public boolean createIfAbsent(
                String id,
                ReferencementDocument document
        ) {
            if (id.equals(failOn)) {
                throw new ReferencementDocumentAccessException(
                        "Échec simulé pour " + id,
                        new IllegalStateException("indisponible")
                );
            }
            return documents.putIfAbsent(id, document) == null;
        }
    }
}
