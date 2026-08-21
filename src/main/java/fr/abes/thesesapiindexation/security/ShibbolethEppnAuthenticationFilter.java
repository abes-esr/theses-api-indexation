package fr.abes.thesesapiindexation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ShibbolethEppnAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String EPPN_HEADER = "eppn";
    private static final String NOINDEX_ADMIN = "NOINDEX_ADMIN";

    private final NoIndexSecurityProperties properties;

    public ShibbolethEppnAuthenticationFilter(
            NoIndexSecurityProperties properties
    ) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        List<String> values = Collections.list(
                request.getHeaders(EPPN_HEADER)
        );
        if (!values.isEmpty()) {
            String eppn = normalize(values);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (properties.allowedEppns().contains(eppn)) {
                authorities.add(new SimpleGrantedAuthority(NOINDEX_ADMIN));
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            eppn,
                            null,
                            authorities
                    )
            );
        }
        filterChain.doFilter(request, response);
    }

    private String normalize(List<String> values) {
        if (values.size() != 1 || values.get(0).isBlank()
                || values.get(0).contains(",")) {
            return "invalid-eppn";
        }
        return values.get(0).trim().toLowerCase(Locale.ROOT);
    }
}
