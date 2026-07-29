package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtParserTest {

    private final RobotsTxtParser parser = new RobotsTxtParser();

    @Test
    void extraitLesTroisTypesDansLeurOrdre() {
        RobotsTxtParseResult result = parser.parse(String.join("\n",
                "Disallow: /2024AIXM0640",
                "Disallow: /270350292",
                "Disallow: /s233841"
        ));

        assertThat(result.entries()).containsExactly(
                new RobotsTxtEntry(
                        "2024AIXM0640",
                        ReferencementPageType.THESE_SOUTENUE
                ),
                new RobotsTxtEntry(
                        "270350292",
                        ReferencementPageType.PERSONNE
                ),
                new RobotsTxtEntry(
                        "s233841",
                        ReferencementPageType.THESE_EN_PREPARATION
                )
        );
    }

    @Test
    void dedupliqueEtClasseChaqueLigne() {
        String content = String.join("\r\n", List.of(
                "User-agent: *",
                "",
                "Disallow: /2024AIXM0640",
                "Disallow: /270350292",
                "Disallow: /s233841",
                "Disallow: /2024AIXM0640",
                "Disallow: /2024AIXM0640.bib",
                "Disallow: /recherche/2024AIXM0640",
                "Disallow: /api",
                "Disallow: /2024BAD",
                "Allow: /270350292",
                "# décision historique"
        ));

        RobotsTxtParseResult result = parser.parse(content);

        assertThat(result.entries()).containsExactly(
                new RobotsTxtEntry(
                        "2024AIXM0640",
                        ReferencementPageType.THESE_SOUTENUE
                ),
                new RobotsTxtEntry(
                        "270350292",
                        ReferencementPageType.PERSONNE
                ),
                new RobotsTxtEntry(
                        "s233841",
                        ReferencementPageType.THESE_EN_PREPARATION
                )
        );
        assertThat(result.totalLines()).isEqualTo(12);
        assertThat(result.valid()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.ignored()).isEqualTo(7);
        assertThat(result.invalid()).isEqualTo(1);
    }

    @Test
    void tolereLesEspacesAutourDeLaDirective() {
        RobotsTxtParseResult result =
                parser.parse("  Disallow :   /14424943X   ");

        assertThat(result.entries()).containsExactly(
                new RobotsTxtEntry(
                        "14424943X",
                        ReferencementPageType.PERSONNE
                )
        );
        assertThat(result.valid()).isEqualTo(1);
        assertThat(result.ignored()).isZero();
    }

    @Test
    void compteLesCandidatsMalformedSansLesImporter() {
        RobotsTxtParseResult result = parser.parse(String.join("\n",
                "Disallow: /2024AIXM064",
                "Disallow: /27035029Y",
                "Disallow: /s233841A"
        ));

        assertThat(result.entries()).isEmpty();
        assertThat(result.invalid()).isEqualTo(3);
        assertThat(result.ignored()).isZero();
    }
}
