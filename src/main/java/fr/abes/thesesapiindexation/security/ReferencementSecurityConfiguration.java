package fr.abes.thesesapiindexation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.io.IOException;
import java.net.URI;

@Configuration
@Profile("!init-index & !import-robots")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(NoIndexSecurityProperties.class)
public class ReferencementSecurityConfiguration {

    @Bean
    ShibbolethEppnAuthenticationFilter shibbolethEppnAuthenticationFilter(
            NoIndexSecurityProperties properties
    ) {
        return new ShibbolethEppnAuthenticationFilter(properties);
    }

    @Bean
    SecurityFilterChain referencementSecurityFilterChain(
            HttpSecurity http,
            ShibbolethEppnAuthenticationFilter authenticationFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/referencements/**"
                        ).hasAuthority("NOINDEX_ADMIN")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response,
                                                   exception) ->
                                writeProblem(
                                        response,
                                        request,
                                        objectMapper,
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentification requise",
                                        "Un ePPN Shibboleth est requis"
                                ))
                        .accessDeniedHandler((request, response,
                                              exception) ->
                                writeProblem(
                                        response,
                                        request,
                                        objectMapper,
                                        HttpStatus.FORBIDDEN,
                                        "Accès refusé",
                                        "Cet ePPN n’est pas autorisé"
                                ))
                )
                .addFilterBefore(
                        authenticationFilter,
                        AnonymousAuthenticationFilter.class
                )
                .build();
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            ObjectMapper objectMapper,
            HttpStatus status,
            String title,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail
        );
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
