package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencementIndexProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReferencementIndexConfiguration.class,
                    GatewayTestConfiguration.class
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
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class GatewayTestConfiguration {

        @Bean
        ReferencementIndexGateway referencementIndexGateway() {
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
}
