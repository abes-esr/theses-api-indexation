package fr.abes.thesesapiindexation.referencement.robots;

import com.sun.net.httpserver.HttpServer;
import fr.abes.thesesapiindexation.ThesesApiIndexationApplication;
import fr.abes.thesesapiindexation.referencement.ReferencementDocument;
import fr.abes.thesesapiindexation.referencement.ReferencementDocumentGateway;
import fr.abes.thesesapiindexation.referencement.ReferencementController;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtImportProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            RobotsTxtImportConfiguration.class
                    )
                    .withBean(
                            RobotsTxtSource.class,
                            () -> () -> ""
                    )
                    .withBean(
                            ReferencementWriteService.class,
                            RobotsTxtImportProfileTest::writeService
                    )
                    .withPropertyValues(
                            "referencement.robots.url=https://theses.fr/robots.txt",
                            "referencement.robots.connect-timeout=5s",
                            "referencement.robots.read-timeout=30s"
                    );

    @Test
    void chargeLeJobUniquementAvecLeProfilImportRobots() {
        contextRunner.run(context -> {
            assertThat(context)
                    .doesNotHaveBean(RobotsTxtImporter.class);
            assertThat(context)
                    .doesNotHaveBean(ApplicationRunner.class);
        });

        contextRunner
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("import-robots"))
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            RobotsTxtParser.class
                    );
                    assertThat(context).hasSingleBean(
                            RobotsTxtImporter.class
                    );
                    assertThat(context).hasSingleBean(
                            ApplicationRunner.class
                    );
                });
    }

    @Test
    void nexposePasLeControleurPendantLImport() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReferencementController.class)
                .withBean(
                        ReferencementWriteService.class,
                        RobotsTxtImportProfileTest::writeService
                )
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("import-robots"))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ReferencementController.class));
    }

    @Test
    void demarreSansContexteWeb() throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext("/robots.txt", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        SpringApplication application = new SpringApplication(
                ThesesApiIndexationApplication.class
        );
        application.setAdditionalProfiles("import-robots");
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "es.hostname", "localhost",
                "es.port", "9200",
                "es.protocol", "http"
        ));
        String robotsUrl = "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/robots.txt";

        try (ConfigurableApplicationContext context =
                     application.run(
                             "--referencement.robots.url=" + robotsUrl
                     )) {
            assertThat(context)
                    .isNotInstanceOf(WebApplicationContext.class);
            RobotsTxtProperties properties =
                    context.getBean(RobotsTxtProperties.class);
            assertThat(properties.url()).hasToString(robotsUrl);
            assertThat(properties.connectTimeout())
                    .isEqualTo(java.time.Duration.ofSeconds(5));
            assertThat(properties.readTimeout())
                    .isEqualTo(java.time.Duration.ofSeconds(30));
        } finally {
            server.stop(0);
        }
    }

    private static ReferencementWriteService writeService() {
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
        return new ReferencementWriteService(
                gateway,
                Clock.fixed(
                        Instant.parse("2026-07-28T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

}
