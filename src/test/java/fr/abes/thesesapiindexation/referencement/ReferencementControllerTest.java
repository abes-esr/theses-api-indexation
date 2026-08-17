package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReferencementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReferencementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReferencementWriteService service;

    @Test
    void activeNoIndexEtRetourneLEtatComplet() throws Exception {
        ReferencementDocument document = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-28T09:15:30Z")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "noIndex": true,
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id")
                        .value("2024AIXM0640"))
                .andExpect(jsonPath("$.pageType")
                        .value("THESE_SOUTENUE"))
                .andExpect(jsonPath("$.noIndex").value(true))
                .andExpect(jsonPath("$.demandeRef")
                        .value("ABESSTP-12345"))
                .andExpect(jsonPath("$.updatedBy")
                        .value("agent@abes.fr"))
                .andExpect(jsonPath("$.updatedAt")
                        .value("2026-07-28T09:15:30Z"));
    }

    @Test
    void rejetteUnChampObligatoireAbsent() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Requête de référencement invalide"));
    }

    @Test
    void rejetteUnChampJsonInconnu() throws Exception {
        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "noIndex": true,
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr",
                                  "motifLibre": "interdit"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));
    }

    @Test
    void rejetteUnIdentifiantIncompatible() throws Exception {
        given(service.write(eq("s233841"), any()))
                .willThrow(new ReferencementValidationException(
                        "s233841",
                        ReferencementPageType.THESE_SOUTENUE
                ));

        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "s233841"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "noIndex": true,
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("L’identifiant s233841 est incompatible avec le type THESE_SOUTENUE"));
    }

    @Test
    void retourneServiceIndisponibleQuandElasticsearchEchoue()
            throws Exception {
        given(service.write(eq("2024AIXM0640"), any()))
                .willThrow(new ReferencementDocumentAccessException(
                        "Impossible d’écrire le référencement 2024AIXM0640 dans l’index referencement",
                        new IOException("Connexion refusée")
                ));

        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "noIndex": true,
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Elasticsearch indisponible"));
    }
}
