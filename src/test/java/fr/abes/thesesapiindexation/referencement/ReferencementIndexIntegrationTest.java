package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.abes.thesesapiindexation.ThesesApiIndexationApplication;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ReferencementIndexIntegrationTest {

    private static final String INDEX_NAME = "referencement";

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.10.3")
            )
                    .withStartupTimeout(Duration.ofMinutes(2));

    private static Path caCertificate;
    private RestClientTransport transport;
    private ElasticsearchClient client;
    private ReferencementIndexInitializer initializer;

    @BeforeAll
    static void copyCaCertificate() throws IOException {
        caCertificate = Files.createTempFile("elasticsearch-ca-", ".crt");
        Files.write(
                caCertificate,
                ELASTICSEARCH.caCertAsBytes().orElseThrow()
        );
    }

    @AfterAll
    static void deleteCaCertificate() throws IOException {
        Files.deleteIfExists(caCertificate);
    }

    @BeforeEach
    void setUp() throws IOException {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "changeme")
        );
        RestClientBuilder restClientBuilder = RestClient.builder(
                HttpHost.create("https://" + ELASTICSEARCH.getHttpHostAddress())
        );
        restClientBuilder.setHttpClientConfigCallback(
                httpClientBuilder -> httpClientBuilder
                        .setSSLContext(ELASTICSEARCH.createSslContextFromCa())
                        .setDefaultCredentialsProvider(credentialsProvider)
        );
        RestClient restClient = restClientBuilder.build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );
        JacksonJsonpMapper mapper = new JacksonJsonpMapper(objectMapper);
        transport = new RestClientTransport(restClient, mapper);
        client = new ElasticsearchClient(transport);

        if (client.indices().exists(request -> request.index(INDEX_NAME)).value()) {
            client.indices().delete(request -> request.index(INDEX_NAME));
        }

        ReferencementIndexGateway gateway =
                new ElasticsearchReferencementIndexGateway(client, mapper);
        ReferencementIndexProperties properties = new ReferencementIndexProperties(
                INDEX_NAME,
                new ClassPathResource("indexs/referencement.json")
        );
        initializer = new ReferencementIndexInitializer(gateway, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        transport.close();
    }

    @Test
    void creeLIndexReferencementDansElasticsearch() throws IOException {
        initializer.initialize();

        assertThat(client.indices().exists(request -> request.index(INDEX_NAME)).value())
                .isTrue();

        GetMappingResponse response = client.indices()
                .getMapping(request -> request.index(INDEX_NAME));
        IndexMappingRecord mapping = response.result().get(INDEX_NAME);

        assertThat(mapping.mappings().dynamic()).isEqualTo(DynamicMapping.Strict);
        assertThat(mapping.mappings().properties().get("noIndex").isBoolean())
                .isTrue();
    }

    @Test
    void valideLeMappingLorsDUnSecondPassage() {
        initializer.initialize();

        assertThatCode(initializer::initialize).doesNotThrowAnyException();
    }

    @Test
    void configureLeClientElasticsearchAvecLeProfilInitIndex() {
        new ApplicationContextRunner()
                .withUserConfiguration(ThesesApiIndexationApplication.class)
                .withInitializer(
                        context -> context.getEnvironment().setActiveProfiles("init-index")
                )
                .withPropertyValues(
                        "es.hostname=" + ELASTICSEARCH.getHost(),
                        "es.port=" + ELASTICSEARCH.getMappedPort(9200),
                        "es.protocol=https",
                        "es.username=elastic",
                        "es.password=changeme",
                        "es.ca-certificate=" + caCertificate.toUri(),
                        "referencement.index.name=" + INDEX_NAME,
                        "referencement.index.mapping=classpath:indexs/referencement.json"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReferencementIndexInitializer configuredInitializer =
                            context.getBean(ReferencementIndexInitializer.class);
                    configuredInitializer.initialize();

                    ElasticsearchClient configuredClient =
                            context.getBean(ElasticsearchClient.class);
                    assertThat(configuredClient.indices()
                            .exists(request -> request.index(INDEX_NAME))
                            .value()).isTrue();
                });
    }

    @Test
    void contextualiseUnRefusDAccesElasticsearch() throws IOException {
        BasicCredentialsProvider invalidCredentials = new BasicCredentialsProvider();
        invalidCredentials.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "mot-de-passe-invalide")
        );
        RestClientBuilder restClientBuilder = RestClient.builder(
                HttpHost.create("https://" + ELASTICSEARCH.getHttpHostAddress())
        );
        restClientBuilder.setHttpClientConfigCallback(
                httpClientBuilder -> httpClientBuilder
                        .setSSLContext(ELASTICSEARCH.createSslContextFromCa())
                        .setDefaultCredentialsProvider(invalidCredentials)
        );

        try (RestClientTransport invalidTransport = new RestClientTransport(
                restClientBuilder.build(),
                new JacksonJsonpMapper()
        )) {
            ReferencementIndexGateway gateway =
                    new ElasticsearchReferencementIndexGateway(
                            new ElasticsearchClient(invalidTransport),
                            new JacksonJsonpMapper()
                    );

            assertThatThrownBy(() -> gateway.mapping(INDEX_NAME))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lire le mapping")
                    .hasMessageContaining(INDEX_NAME)
                    .hasCauseInstanceOf(ElasticsearchException.class);
        }
    }

    @Test
    void termineLeProcessusApresLInitialisation() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java"
                ).toString(),
                "-cp",
                System.getProperty("surefire.test.class.path"),
                ThesesApiIndexationApplication.class.getName(),
                "--spring.profiles.active=init-index",
                "--es.hostname=" + ELASTICSEARCH.getHost(),
                "--es.port=" + ELASTICSEARCH.getMappedPort(9200),
                "--es.protocol=https",
                "--es.username=elastic",
                "--es.password=changeme",
                "--es.ca-certificate=" + caCertificate.toUri(),
                "--referencement.index.name=" + INDEX_NAME,
                "--referencement.index.mapping=classpath:indexs/referencement.json"
        )
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor();
        }
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }

    @Test
    void importeLeRobotsTxtPuisTermineLeProcessus() throws Exception {
        initializer.initialize();
        ReferencementDocumentGateway gateway =
                new ElasticsearchReferencementDocumentGateway(
                        client,
                        INDEX_NAME
                );
        ReferencementDocument existing = new ReferencementDocument(
                ReferencementPageType.PERSONNE,
                false,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-27T08:00:00Z")
        );
        gateway.save("270350292", existing);

        String robots = String.join("\n", List.of(
                "Disallow: /2024AIXM0640",
                "Disallow: /270350292",
                "Disallow: /s233841",
                "Disallow: /2024AIXM0640",
                "Disallow: /2024AIXM0640.bib"
        ));
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext("/robots.txt", exchange -> {
            byte[] body = robots.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Process process = importProcess(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort()
                            + "/robots.txt"
            );
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly().waitFor();
            }
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            assertThat(finished).as(output).isTrue();
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains(
                    "lignes=5, valides=3, doublons=1, ignorées=1, "
                            + "invalides=0, créés=2, existants=1"
            );
            assertThat(gateway.findById("2024AIXM0640"))
                    .get()
                    .extracting(
                            ReferencementDocument::pageType,
                            ReferencementDocument::noIndex,
                            ReferencementDocument::demandeRef,
                            ReferencementDocument::updatedBy
                    )
                    .containsExactly(
                            ReferencementPageType.THESE_SOUTENUE,
                            true,
                            "IMPORT-ROBOTS-INITIAL",
                            "robots.txt-importer"
                    );
            assertThat(gateway.findById("270350292"))
                    .contains(existing);
            assertThat(gateway.findById("s233841"))
                    .get()
                    .extracting(ReferencementDocument::pageType)
                    .isEqualTo(
                            ReferencementPageType.THESE_EN_PREPARATION
                    );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void echoueSansEcrireQuandLaSourceHttpEstIndisponible()
            throws Exception {
        initializer.initialize();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext("/robots.txt", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        try {
            Process process = importProcess(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort()
                            + "/robots.txt"
            );
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly().waitFor();
            }
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            assertThat(finished).as(output).isTrue();
            assertThat(process.exitValue()).as(output).isNotZero();
            assertThat(client.count(
                    request -> request.index(INDEX_NAME)
            ).count()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @MethodSource("documentsToPersist")
    void ecritEtRelitLesTroisTypesDIdentifiants(
            String id,
            ReferencementDocument document
    ) {
        initializer.initialize();
        ReferencementDocumentGateway documentGateway =
                new ElasticsearchReferencementDocumentGateway(
                        client,
                        INDEX_NAME
                );

        documentGateway.save(id, document);

        assertThat(documentGateway.findById(id)).contains(document);
    }

    @Test
    void remplaceLeDocumentLorsDeLaReactivation() {
        initializer.initialize();
        ReferencementDocumentGateway documentGateway =
                new ElasticsearchReferencementDocumentGateway(
                        client,
                        INDEX_NAME
                );
        ReferencementDocument active = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-28T09:15:30Z")
        );
        ReferencementDocument inactive = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                false,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-28T10:00:00Z")
        );

        documentGateway.save("2024AIXM0640", active);
        documentGateway.save("2024AIXM0640", inactive);

        assertThat(documentGateway.findById("2024AIXM0640"))
                .contains(inactive);
    }

    @Test
    void neRemplacePasUnDocumentExistantPendantUnImport() {
        initializer.initialize();
        ReferencementDocumentGateway documentGateway =
                new ElasticsearchReferencementDocumentGateway(
                        client,
                        INDEX_NAME
                );
        ReferencementDocument existing = new ReferencementDocument(
                ReferencementPageType.PERSONNE,
                false,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-28T09:15:30Z")
        );
        ReferencementDocument imported = new ReferencementDocument(
                ReferencementPageType.PERSONNE,
                true,
                "IMPORT-ROBOTS-INITIAL",
                "robots.txt-importer",
                Instant.parse("2026-07-28T10:00:00Z")
        );
        documentGateway.save("270350292", existing);

        boolean created =
                documentGateway.createIfAbsent("270350292", imported);

        assertThat(created).isFalse();
        assertThat(documentGateway.findById("270350292"))
                .contains(existing);
    }

    private static Stream<Arguments> documentsToPersist() {
        Instant updatedAt = Instant.parse("2026-07-28T09:15:30Z");
        return Stream.of(
                Arguments.of(
                        "2024AIXM0640",
                        new ReferencementDocument(
                                ReferencementPageType.THESE_SOUTENUE,
                                true,
                                "ABESSTP-12345",
                                "agent@abes.fr",
                                updatedAt
                        )
                ),
                Arguments.of(
                        "270350292",
                        new ReferencementDocument(
                                ReferencementPageType.PERSONNE,
                                true,
                                "ABESSTP-12345",
                                "agent@abes.fr",
                                updatedAt
                        )
                ),
                Arguments.of(
                        "s233841",
                        new ReferencementDocument(
                                ReferencementPageType.THESE_EN_PREPARATION,
                                true,
                                "ABESSTP-12345",
                                "agent@abes.fr",
                                updatedAt
                        )
                )
        );
    }

    private Process importProcess(String robotsUrl) throws IOException {
        return new ProcessBuilder(
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java"
                ).toString(),
                "-cp",
                System.getProperty("surefire.test.class.path"),
                ThesesApiIndexationApplication.class.getName(),
                "--spring.profiles.active=import-robots",
                "--es.hostname=" + ELASTICSEARCH.getHost(),
                "--es.port=" + ELASTICSEARCH.getMappedPort(9200),
                "--es.protocol=https",
                "--es.username=elastic",
                "--es.password=changeme",
                "--es.ca-certificate=" + caCertificate.toUri(),
                "--referencement.index.name=" + INDEX_NAME,
                "--referencement.robots.url=" + robotsUrl
        )
                .redirectErrorStream(true)
                .start();
    }
}
