package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@Profile("import-robots")
@EnableConfigurationProperties(RobotsTxtProperties.class)
public class RobotsTxtImportConfiguration {

    @Bean
    HttpClient robotsTxtHttpClient(RobotsTxtProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(RobotsTxtSource.class)
    RobotsTxtSource robotsTxtSource(
            HttpClient robotsTxtHttpClient,
            RobotsTxtProperties properties
    ) {
        return new HttpRobotsTxtSource(
                robotsTxtHttpClient,
                properties
        );
    }

    @Bean
    RobotsTxtParser robotsTxtParser() {
        return new RobotsTxtParser();
    }

    @Bean
    RobotsTxtImporter robotsTxtImporter(
            RobotsTxtSource source,
            RobotsTxtParser parser,
            ReferencementWriteService writeService
    ) {
        return new RobotsTxtImporter(source, parser, writeService);
    }

    @Bean
    ApplicationRunner robotsTxtImportRunner(
            RobotsTxtImporter importer
    ) {
        return new RobotsTxtImportRunner(importer);
    }
}
