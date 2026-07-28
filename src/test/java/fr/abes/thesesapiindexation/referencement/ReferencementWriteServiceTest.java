package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final class RecordingGateway
            implements ReferencementDocumentGateway {
        private String savedId;
        private ReferencementDocument savedDocument;
        private ReferencementDocument existingDocument;
        private int saveCount;

        @Override
        public Optional<ReferencementDocument> findById(String id) {
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
