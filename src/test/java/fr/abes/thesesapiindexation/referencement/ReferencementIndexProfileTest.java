package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import fr.abes.thesesapiindexation.ThesesApiIndexationApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencementIndexProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReferencementIndexConfiguration.class)
            .withBean(
                    ReferencementIndexGateway.class,
                    ReferencementIndexProfileTest::gatewayDeTest
            )
            .withPropertyValues(
                    "referencement.index.name=referencement",
                    "referencement.index.mapping=classpath:indexs/referencement.json"
            );

    @Test
    void neChargePasLInitialiseurSansLeProfilInitIndex() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ReferencementIndexInitializer.class);
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }

    @Test
    void chargeLInitialiseurAvecLeProfilInitIndex() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("init-index"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ReferencementIndexInitializer.class);
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                    assertThat(context).hasSingleBean(ReferencementIndexGateway.class);
                    assertThat(context)
                            .doesNotHaveBean(ReferencementWriteService.class);
                });
    }

    @Test
    void chargeLeClientEtLeServiceDEcritureHorsDuProfilInitIndex() {
        new ApplicationContextRunner()
                .withUserConfiguration(ThesesApiIndexationApplication.class)
                .withPropertyValues(
                        "es.hostname=localhost",
                        "es.port=9200",
                        "es.protocol=http"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(ElasticsearchClient.class);
                    assertThat(context)
                            .hasSingleBean(ReferencementDocumentGateway.class);
                    assertThat(context)
                            .hasSingleBean(ReferencementWriteService.class);
                    assertThat(context)
                            .doesNotHaveBean(ReferencementIndexGateway.class);
                    assertThat(context)
                            .doesNotHaveBean(ReferencementIndexInitializer.class);
                });
    }

    private static ReferencementIndexGateway gatewayDeTest() {
        return new ReferencementIndexGateway() {
            @Override
            public boolean exists(String indexName) {
                return false;
            }

            @Override
            public void create(String indexName, InputStream mapping) {
            }

            @Override
            public InputStream mapping(String indexName) {
                return InputStream.nullInputStream();
            }
        };
    }
}
