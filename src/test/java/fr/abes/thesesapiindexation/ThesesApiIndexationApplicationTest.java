package fr.abes.thesesapiindexation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ThesesApiIndexationApplicationTest {

    @Test
    void reconnaitUniquementLesProfilsPonctuels() {
        MockEnvironment normal = new MockEnvironment();
        MockEnvironment init = new MockEnvironment();
        init.setActiveProfiles("init-index");
        MockEnvironment importRobots = new MockEnvironment();
        importRobots.setActiveProfiles("import-robots");

        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(normal)).isFalse();
        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(init)).isTrue();
        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(importRobots)).isTrue();
    }
}
