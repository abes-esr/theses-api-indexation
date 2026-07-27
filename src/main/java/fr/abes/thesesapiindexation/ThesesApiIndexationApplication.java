package fr.abes.thesesapiindexation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
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
        if (context.getEnvironment().acceptsProfiles(Profiles.of("init-index"))) {
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }
}
