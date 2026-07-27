package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
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
}
