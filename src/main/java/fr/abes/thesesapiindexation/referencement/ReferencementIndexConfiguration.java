package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("init-index")
@EnableConfigurationProperties(ReferencementIndexProperties.class)
public class ReferencementIndexConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReferencementIndexGateway.class)
    ReferencementIndexGateway referencementIndexGateway(
            ElasticsearchClient elasticsearchClient,
            JacksonJsonpMapper elasticsearchJsonpMapper
    ) {
        return new ElasticsearchReferencementIndexGateway(
                elasticsearchClient,
                elasticsearchJsonpMapper
        );
    }

    @Bean
    ReferencementIndexInitializer referencementIndexInitializer(
            ReferencementIndexGateway gateway,
            ReferencementIndexProperties properties
    ) {
        return new ReferencementIndexInitializer(gateway, properties);
    }

    @Bean
    ApplicationRunner referencementIndexApplicationRunner(
            ReferencementIndexInitializer initializer
    ) {
        return arguments -> initializer.initialize();
    }
}
