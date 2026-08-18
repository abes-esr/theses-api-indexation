package fr.abes.thesesapiindexation.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoIndexSecurityPropertiesTest {

    @Test
    void normaliseLesEspacesLaCasseEtLesDoublons() {
        NoIndexSecurityProperties properties =
                new NoIndexSecurityProperties(
                        " Agent@Abes.fr,agent@abes.fr , second@abes.fr "
                );

        assertThat(properties.allowedEppns())
                .containsExactly(
                        "agent@abes.fr",
                        "second@abes.fr"
                );
    }

    @Test
    void refuseUnJokerDansLaListeDesEppns() {
        assertThatThrownBy(
                () -> new NoIndexSecurityProperties("*@abes.fr")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Les jokers sont interdits dans la liste des ePPN");
    }

    @Test
    void uneListeVideNautoriseAucunEppn() {
        assertThat(new NoIndexSecurityProperties("  ").allowedEppns())
                .isEmpty();
    }
}
