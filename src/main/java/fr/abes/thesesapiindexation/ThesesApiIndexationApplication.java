package fr.abes.thesesapiindexation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@SpringBootApplication(exclude = {
        ElasticsearchClientAutoConfiguration.class,
        ElasticsearchRestClientAutoConfiguration.class
})
public class ThesesApiIndexationApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(
                ThesesApiIndexationApplication.class,
                args
        );
        if (isOneShotProfile(context.getEnvironment())) {
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }

    static boolean isOneShotProfile(Environment environment) {
        return environment.acceptsProfiles(
                Profiles.of("init-index | import-robots")
        );
    }
}
