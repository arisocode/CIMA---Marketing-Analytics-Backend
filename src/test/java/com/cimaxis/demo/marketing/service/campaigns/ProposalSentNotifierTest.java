package com.cimaxis.demo.marketing.service.campaigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.EmailService;

/** Aviso al cliente cuando CIMA le envia una propuesta. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalSentNotifierTest {

    @Mock private EmailService emailService;
    @Mock private ClientRepository clientRepository;

    private ProposalSentNotifier notifier;

    @BeforeEach
    void setUp() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(Client.builder().clientId("c-1")
                .additionalInfo("Ana Pérez").contactInfo("ana@cima.co").build()));
        when(clientRepository.findById("c-x")).thenReturn(Optional.empty());
        notifier = new ProposalSentNotifier(emailService, clientRepository);
    }

    @Test
    @DisplayName("Envia el correo al contactInfo con la plantilla de marca y el cupo de EmailService")
    void enviaCorreo() {
        when(emailService.send(eq("ana@cima.co"), anyString(), anyString(), eq(EmailService.ORIGEN_PROPUESTA)))
                .thenReturn(DispatchResult.queued("email", "Encolado"));

        DispatchResult resultado = notifier.notifySent(Proposal.builder().proposalId(7).clientId("c-1")
                .description("Branding 2027").estimatedValue(new BigDecimal("2500000")).build());

        assertThat(resultado.delivered()).isTrue();
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("ana@cima.co"), asunto.capture(), cuerpo.capture(), eq(EmailService.ORIGEN_PROPUESTA));
        assertThat(asunto.getValue()).isEqualTo("Tienes una nueva propuesta de CIMA");
        assertThat(cuerpo.getValue()).contains("Hola Ana Pérez");
        assertThat(cuerpo.getValue()).contains("Branding 2027");
        assertThat(cuerpo.getValue()).contains("2500000.00");
    }

    @Test
    @DisplayName("Sin cliente no se envia nada")
    void sinCliente() {
        DispatchResult resultado = notifier.notifySent(Proposal.builder().proposalId(8).clientId("c-x").build());

        assertThat(resultado.delivered()).isFalse();
        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un error del correo no se propaga")
    void errorNoSePropaga() {
        when(emailService.send(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("caido"));

        DispatchResult resultado = notifier.notifySent(Proposal.builder().proposalId(7).clientId("c-1").build());

        assertThat(resultado.delivered()).isFalse();
    }
}
