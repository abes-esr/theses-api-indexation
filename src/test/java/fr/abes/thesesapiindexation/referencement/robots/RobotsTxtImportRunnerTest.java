package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementDocument;
import fr.abes.thesesapiindexation.referencement.ReferencementDocumentGateway;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RobotsTxtImportRunnerTest {

    @Test
    void journaliseTousLesCompteurs(CapturedOutput output)
            throws Exception {
        ReferencementDocumentGateway gateway =
                new ReferencementDocumentGateway() {
                    @Override
                    public Optional<ReferencementDocument> findById(
                            String id
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public void save(
                            String id,
                            ReferencementDocument document
                    ) {
                    }

                    @Override
                    public boolean createIfAbsent(
                            String id,
                            ReferencementDocument document
                    ) {
                        return true;
                    }
                };
        RobotsTxtImporter importer = new RobotsTxtImporter(
                () -> "Disallow: /270350292",
                new RobotsTxtParser(),
                new ReferencementWriteService(
                        gateway,
                        Clock.systemUTC()
                )
        );

        new RobotsTxtImportRunner(importer).run(
                new DefaultApplicationArguments()
        );

        assertThat(output).contains(
                "lignes=1, valides=1, doublons=0, ignorées=0, "
                        + "invalides=0, créés=1, existants=0"
        );
    }

    @Test
    void journaliseLeBilanPartielAvantDePropagerLEchec(
            CapturedOutput output
    ) {
        AtomicInteger calls = new AtomicInteger();
        ReferencementDocumentGateway gateway =
                new ReferencementDocumentGateway() {
                    @Override
                    public Optional<ReferencementDocument> findById(
                            String id
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public void save(
                            String id,
                            ReferencementDocument document
                    ) {
                    }

                    @Override
                    public boolean createIfAbsent(
                            String id,
                            ReferencementDocument document
                    ) {
                        if (calls.incrementAndGet() == 2) {
                            throw new IllegalStateException(
                                    "Elasticsearch indisponible"
                            );
                        }
                        return true;
                    }
                };
        RobotsTxtImporter importer = new RobotsTxtImporter(
                () -> String.join("\n",
                        "Disallow: /2024AIXM0640",
                        "Disallow: /270350292"
                ),
                new RobotsTxtParser(),
                new ReferencementWriteService(
                        gateway,
                        Clock.systemUTC()
                )
        );
        RobotsTxtImportRunner runner =
                new RobotsTxtImportRunner(importer);

        assertThatThrownBy(() -> runner.run(
                new DefaultApplicationArguments()
        )).isInstanceOf(RobotsTxtImportException.class);

        assertThat(output).contains(
                "interrompu sur 270350292 : lignes=2, valides=2, "
                        + "doublons=0, ignorées=0, invalides=0, "
                        + "créés=1, existants=0"
        );
    }
}
