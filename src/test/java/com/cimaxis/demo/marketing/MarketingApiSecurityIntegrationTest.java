package com.cimaxis.demo.marketing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica el contrato HTTP de Marketing en el límite de seguridad real.
 * Los roles provienen de las cabeceras confiables que inyecta el gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketingApiSecurityIntegrationTest {

    private static final String ADMIN_SUB = "00000000-0000-4000-8000-000000000001";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_database")
            .withUsername("marketing_user")
            .withPassword("marketingpassword_test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("DB_SCHEMA", () -> "public");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rechazaMarketingSinIdentidadDeGateway() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/campaigns"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaMarketingParaCliente() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/campaigns")
                        .header("X-User-Sub", "00000000-0000-4000-8000-000000000002")
                        .header("X-User-Role", "client"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteOperacionDeMarketingParaAdministradorYConservaElActor() throws Exception {
        String campaign = """
                {
                  "campaignName": "Campaña de integración",
                  "campaignType": "Direct_sales",
                  "clientId": "00000000-0000-4000-8000-000000000003",
                  "startDate": "2026-08-26",
                  "status": "Active",
                  "platforms": "Web",
                  "objective": "Validar contrato HTTP"
                }
                """;

        mockMvc.perform(post("/api/v1/marketing/campaigns")
                        .header("X-User-Sub", ADMIN_SUB)
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaign))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignName").value("Campaña de integración"))
                .andExpect(jsonPath("$.createdBy").value(ADMIN_SUB));

        mockMvc.perform(get("/api/v1/marketing/campaigns")
                        .header("X-User-Sub", ADMIN_SUB)
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campaignName").value("Campaña de integración"));
    }
}
