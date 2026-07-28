package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("!init-index")
@EnableConfigurationProperties(ReferencementIndexProperties.class)
public class ReferencementWriteConfiguration {

    @Bean
    Clock referencementClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReferencementDocumentGateway referencementDocumentGateway(
            ElasticsearchClient client,
            ReferencementIndexProperties properties
    ) {
        return new ElasticsearchReferencementDocumentGateway(
                client,
                properties.name()
        );
    }

    @Bean
    ReferencementWriteService referencementWriteService(
            ReferencementDocumentGateway gateway,
            Clock referencementClock
    ) {
        return new ReferencementWriteService(
                gateway,
                referencementClock
        );
    }
}
