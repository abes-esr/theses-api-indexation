package fr.abes.thesesapiindexation.security;

import fr.abes.thesesapiindexation.referencement.ReferencementController;
import fr.abes.thesesapiindexation.referencement.ReferencementDocument;
import fr.abes.thesesapiindexation.referencement.ReferencementPageType;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteResult;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReferencementController.class)
@Import(ReferencementSecurityConfiguration.class)
@TestPropertySource(properties =
        "referencement.security.allowed-eppns=agent@abes.fr")
class ReferencementSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReferencementWriteService service;

    @Test
    void refuseUneEcritureSansEnteteEppnAvecUnCode401()
            throws Exception {
        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Authentification requise"));
    }

    @Test
    void refuseUnEppnNonAutoriseAvecUnCode403() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .header("eppn", "intrus@abes.fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Accès refusé"));
    }

    @Test
    void autoriseUnEppnDeclareMalgreLaCasseEtLesEspaces()
            throws Exception {
        ReferencementDocument document = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-08-17T14:30:00Z")
        );
        given(service.write(eq("2024AIXM0640"), any()))
                .willReturn(new ReferencementWriteResult(
                        "2024AIXM0640",
                        document
                ));

        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .header("eppn", " Agent@Abes.FR ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk());
    }

    @Test
    void refusePlusieursValeursPourLEnteteEppn() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .header(
                                "eppn",
                                "agent@abes.fr",
                                "second@abes.fr"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuseUneRouteNonDeclareeMemePourUnEppnAutorise()
            throws Exception {
        mockMvc.perform(put("/api/v1/inconnue")
                        .header("eppn", "agent@abes.fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private String validRequest() {
        return """
                {
                  "pageType": "THESE_SOUTENUE",
                  "noIndex": true,
                  "demandeRef": "ABESSTP-12345"
                }
                """;
    }
}
