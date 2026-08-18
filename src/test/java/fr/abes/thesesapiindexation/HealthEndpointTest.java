package fr.abes.thesesapiindexation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @Test
    void exposeUneSanteUpSansDetailsEtSansEppn() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void nexposeAucunAutreEndpointActuator() {
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");
    }
}
