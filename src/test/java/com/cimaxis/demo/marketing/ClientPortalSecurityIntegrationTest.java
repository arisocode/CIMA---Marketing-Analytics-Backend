package com.cimaxis.demo.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Limite de seguridad del portal del cliente: /api/v1/marketing/me/** solo para el
 * rol client, el resto de marketing y analitica solo para admin y worker, y un
 * cliente nunca ve ni decide propuestas de otro (404, no 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClientPortalSecurityIntegrationTest {

    private static final String ANA = "00000000-0000-4000-8000-0000000000a1";
    private static final String LUIS = "00000000-0000-4000-8000-0000000000b2";
    private static final String SIN_SINCRONIZAR = "00000000-0000-4000-8000-0000000000c3";
    private static final String ADMIN = "00000000-0000-4000-8000-000000000001";
    private static final String WORKER = "00000000-0000-4000-8000-000000000002";

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private MarketingInteractionRepository interactionRepository;

    private Integer propuestaDeAna;
    private Integer propuestaDeLuis;

    @BeforeEach
    void datos() {
        interactionRepository.deleteAll();
        proposalRepository.deleteAll();
        clientRepository.deleteAll();
        clientRepository.saveAll(List.of(cliente(ANA, "Ana"), cliente(LUIS, "Luis")));
        propuestaDeAna = proposalRepository.save(propuesta(ANA, "Branding Ana")).getProposalId();
        propuestaDeLuis = proposalRepository.save(propuesta(LUIS, "Branding Luis")).getProposalId();
    }

    @Test
    void elClienteVeSoloSusPropuestas() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/me/proposals")
                        .header("X-User-Sub", ANA)
                        .header("X-User-Role", "client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proposalId").value(propuestaDeAna))
                .andExpect(jsonPath("$[0].title").value("Branding Ana"));
    }

    @Test
    void elClienteNoAccedeAlBackOffice() throws Exception {
        for (String ruta : List.of("/api/v1/marketing/campaigns", "/api/v1/analytics/kpis",
                "/api/v1/marketing/messages/threads", "/api/v1/marketing/proposals")) {
            mockMvc.perform(get(ruta)
                            .header("X-User-Sub", ANA)
                            .header("X-User-Role", "client"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminYWorkerNoAccedenAlPortal() throws Exception {
        for (String[] usuario : List.of(new String[] {ADMIN, "admin"}, new String[] {WORKER, "worker"})) {
            mockMvc.perform(get("/api/v1/marketing/me/proposals")
                            .header("X-User-Sub", usuario[0])
                            .header("X-User-Role", usuario[1]))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/marketing/me/summary")
                            .header("X-User-Sub", usuario[0])
                            .header("X-User-Role", usuario[1]))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void elPortalExigeIdentidadDelGateway() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/me/proposals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unClienteNoDecideLaPropuestaDeOtro() throws Exception {
        mockMvc.perform(patch("/api/v1/marketing/me/proposals/" + propuestaDeLuis + "/decision")
                        .header("X-User-Sub", ANA)
                        .header("X-User-Role", "client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());

        assertThat(proposalRepository.findById(propuestaDeLuis).orElseThrow().getStatus())
                .isEqualTo(Proposal.ProposalStatus.Sent);
        assertThat(interactionRepository.count()).isZero();
    }

    @Test
    void elClienteDecideSuPropuestaYQuedaUnaInteraccionSinCampana() throws Exception {
        mockMvc.perform(patch("/api/v1/marketing/me/proposals/" + propuestaDeAna + "/decision")
                        .header("X-User-Sub", ANA)
                        .header("X-User-Role", "client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPTED\",\"comment\":\"Adelante\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Approved"))
                .andExpect(jsonPath("$.clientComment").value("Adelante"));

        List<MarketingInteraction> interacciones = interactionRepository.findByClientId(ANA);
        assertThat(interacciones).hasSize(1);
        assertThat(interacciones.get(0).getCampaignId()).isNull();

        mockMvc.perform(patch("/api/v1/marketing/me/proposals/" + propuestaDeAna + "/decision")
                        .header("X-User-Sub", ANA)
                        .header("X-User-Role", "client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Cambié de opinión\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cuentaSinClienteSincronizadoResponde404ConMensajeClaro() throws Exception {
        mockMvc.perform(get("/api/v1/marketing/me/summary")
                        .header("X-User-Sub", SIN_SINCRONIZAR)
                        .header("X-User-Role", "client"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Tu cuenta aún no está sincronizada con marketing"));
    }

    private static Client cliente(String id, String nombre) {
        return Client.builder().clientId(id).userId(id).additionalInfo(nombre)
                .contactInfo(nombre.toLowerCase() + "@example.test").createdAt(LocalDateTime.now()).build();
    }

    private static Proposal propuesta(String clientId, String descripcion) {
        return Proposal.builder().clientId(clientId).description(descripcion)
                .status(Proposal.ProposalStatus.Sent).estimatedValue(new BigDecimal("1000000"))
                .createdDate(LocalDate.now()).createdAt(LocalDateTime.now()).build();
    }
}
