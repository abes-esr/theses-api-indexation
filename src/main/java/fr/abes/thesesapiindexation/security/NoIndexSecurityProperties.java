package fr.abes.thesesapiindexation.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "referencement.security")
public class NoIndexSecurityProperties {

    private final List<String> allowedEppns;

    @ConstructorBinding
    public NoIndexSecurityProperties(String allowedEppns) {
        this.allowedEppns = normalize(allowedEppns);
    }

    public List<String> allowedEppns() {
        return allowedEppns;
    }

    private List<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(eppn -> !eppn.isEmpty())
                .map(eppn -> eppn.toLowerCase(Locale.ROOT))
                .peek(this::rejectWildcard)
                .distinct()
                .toList();
    }

    private void rejectWildcard(String eppn) {
        if (eppn.contains("*")) {
            throw new IllegalArgumentException(
                    "Les jokers sont interdits dans la liste des ePPN"
            );
        }
    }
}
