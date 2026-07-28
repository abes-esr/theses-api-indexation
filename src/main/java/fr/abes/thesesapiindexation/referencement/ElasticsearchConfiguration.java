package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

@Configuration(proxyBeanMethods = false)
@Profile("init-index")
@EnableConfigurationProperties(ElasticsearchConnectionProperties.class)
public class ElasticsearchConfiguration {

    @Bean(destroyMethod = "close")
    RestClient elasticsearchRestClient(ElasticsearchConnectionProperties properties)
            throws GeneralSecurityException, IOException {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(
                        properties.hostname(),
                        properties.port(),
                        properties.protocol()
                )
        );

        CredentialsProvider credentialsProvider = credentialsProvider(properties);
        SSLContext sslContext = sslContext(properties.caCertificate());
        if (credentialsProvider != null || sslContext != null) {
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    configureHttpClient(httpClientBuilder, credentialsProvider, sslContext));
        }

        return builder.build();
    }

    private CredentialsProvider credentialsProvider(ElasticsearchConnectionProperties properties) {
        if (StringUtils.hasText(properties.username())) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            properties.username(),
                            properties.password()
                    )
            );
            return credentialsProvider;
        }
        return null;
    }

    private HttpAsyncClientBuilder configureHttpClient(
            HttpAsyncClientBuilder httpClientBuilder,
            CredentialsProvider credentialsProvider,
            SSLContext sslContext
    ) {
        if (credentialsProvider != null) {
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }
        if (sslContext != null) {
            httpClientBuilder.setSSLContext(sslContext);
        }
        return httpClientBuilder;
    }

    private SSLContext sslContext(Resource caCertificate)
            throws GeneralSecurityException, IOException {
        if (caCertificate == null) {
            return null;
        }

        Certificate certificate;
        try (InputStream inputStream = caCertificate.getInputStream()) {
            certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(inputStream);
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("elasticsearch-ca", certificate);

        return SSLContexts.custom()
                .loadTrustMaterial(trustStore, null)
                .build();
    }

    @Bean
    JacksonJsonpMapper elasticsearchJsonpMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );
        return new JacksonJsonpMapper(objectMapper);
    }

    @Bean(destroyMethod = "")
    RestClientTransport elasticsearchTransport(
            RestClient elasticsearchRestClient,
            JacksonJsonpMapper elasticsearchJsonpMapper
    ) {
        return new RestClientTransport(
                elasticsearchRestClient,
                elasticsearchJsonpMapper
        );
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClientTransport elasticsearchTransport) {
        return new ElasticsearchClient(elasticsearchTransport);
    }

    @Bean
    ReferencementIndexGateway referencementIndexGateway(
            ElasticsearchClient elasticsearchClient,
            JacksonJsonpMapper elasticsearchJsonpMapper
    ) {
        return new ElasticsearchReferencementIndexGateway(
                elasticsearchClient,
                elasticsearchJsonpMapper
        );
    }
}
