package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferencementWriteServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T09:15:30Z");

    @Test
    void activeNoIndexPourUnNnt() {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReferencementWriteCommand command = new ReferencementWriteCommand(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr"
        );

        ReferencementWriteResult result =
                service.write("2024AIXM0640", command);

        assertThat(result.id()).isEqualTo("2024AIXM0640");
        assertThat(result.document()).isEqualTo(new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                NOW
        ));
        assertThat(gateway.savedId).isEqualTo("2024AIXM0640");
        assertThat(gateway.savedDocument).isEqualTo(result.document());
    }

    @Test
    void reactiveLIndexationEnConservantLeDocument() {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service.write(
                "2024AIXM0640",
                new ReferencementWriteCommand(
                        ReferencementPageType.THESE_SOUTENUE,
                        true,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        ReferencementWriteResult result = service.write(
                "2024AIXM0640",
                new ReferencementWriteCommand(
                        ReferencementPageType.THESE_SOUTENUE,
                        false,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        assertThat(result.document().noIndex()).isFalse();
        assertThat(result.document().updatedAt()).isEqualTo(NOW);
        assertThat(gateway.savedId).isEqualTo("2024AIXM0640");
        assertThat(gateway.savedDocument).isEqualTo(result.document());
    }

    @Test
    void conserveUpdatedAtQuandLaRequeteEstIdentique() {
        Instant previousUpdate = Instant.parse("2026-07-27T08:00:00Z");
        RecordingGateway gateway = new RecordingGateway();
        gateway.existingDocument = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                previousUpdate
        );
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ReferencementWriteResult result = service.write(
                "2024AIXM0640",
                new ReferencementWriteCommand(
                        ReferencementPageType.THESE_SOUTENUE,
                        true,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        assertThat(result.document().updatedAt()).isEqualTo(previousUpdate);
        assertThat(gateway.saveCount).isZero();
    }

    @ParameterizedTest
    @MethodSource("validIdentifiers")
    void accepteLesIdentifiantsCanoniques(
            ReferencementPageType pageType,
            String id
    ) {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.write(
                id,
                new ReferencementWriteCommand(
                        pageType,
                        true,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        assertThat(gateway.savedId).isEqualTo(id);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void rejetteUnIdentifiantIncompatible(
            ReferencementPageType pageType,
            String id
    ) {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.write(
                id,
                new ReferencementWriteCommand(
                        pageType,
                        true,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        ))
                .isInstanceOf(ReferencementValidationException.class)
                .hasMessageContaining(id)
                .hasMessageContaining(pageType.name());

        assertThat(gateway.findCount).isZero();
        assertThat(gateway.saveCount).isZero();
    }

    private static Stream<Arguments> validIdentifiers() {
        return Stream.of(
                Arguments.of(
                        ReferencementPageType.THESE_SOUTENUE,
                        "2024AIXM0640"
                ),
                Arguments.of(
                        ReferencementPageType.PERSONNE,
                        "270350292"
                ),
                Arguments.of(
                        ReferencementPageType.PERSONNE,
                        "14424943X"
                ),
                Arguments.of(
                        ReferencementPageType.THESE_EN_PREPARATION,
                        "s233841"
                )
        );
    }

    private static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(
                Arguments.of(
                        ReferencementPageType.THESE_SOUTENUE,
                        "s233841"
                ),
                Arguments.of(
                        ReferencementPageType.THESE_SOUTENUE,
                        "2024aixm0640"
                ),
                Arguments.of(
                        ReferencementPageType.PERSONNE,
                        "27035029"
                ),
                Arguments.of(
                        ReferencementPageType.PERSONNE,
                        "14424943x"
                ),
                Arguments.of(
                        ReferencementPageType.THESE_EN_PREPARATION,
                        "S233841"
                ),
                Arguments.of(
                        ReferencementPageType.THESE_EN_PREPARATION,
                        "s233841 "
                )
        );
    }

    private static final class RecordingGateway
            implements ReferencementDocumentGateway {
        private String savedId;
        private ReferencementDocument savedDocument;
        private ReferencementDocument existingDocument;
        private int findCount;
        private int saveCount;

        @Override
        public Optional<ReferencementDocument> findById(String id) {
            findCount++;
            return Optional.ofNullable(existingDocument);
        }

        @Override
        public void save(String id, ReferencementDocument document) {
            savedId = id;
            savedDocument = document;
            saveCount++;
        }
    }
}
