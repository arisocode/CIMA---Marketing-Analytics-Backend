package com.cimaxis.demo.marketing.service.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;
import com.cimaxis.demo.marketing.dto.templates.TemplatePreviewResponse;
import com.cimaxis.demo.marketing.dto.templates.TemplateRequest;
import com.cimaxis.demo.marketing.dto.templates.TemplateResponse;
import com.cimaxis.demo.marketing.repository.templates.MessageTemplateRepository;
import com.cimaxis.demo.marketing.service.plans.MarketingPlanService;

/** Biblioteca de plantillas: validaciones, filtros, activacion y vista previa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageTemplateServiceTest {

    @Mock private MessageTemplateRepository templateRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private MarketingPlanService planService;

    private MessageTemplateService service;

    private final MessageTemplate bienvenidaOro = plantilla("Bienvenida Oro", MessageTemplate.Channel.EMAIL, "Oro", true);
    private final MessageTemplate general = plantilla("Recordatorio", MessageTemplate.Channel.EMAIL, null, true);
    private final MessageTemplate whatsappDiamante = plantilla("Oferta", MessageTemplate.Channel.WHATSAPP, "Diamante", true);
    private final MessageTemplate vieja = plantilla("Antigua", MessageTemplate.Channel.EMAIL, null, false);

    @BeforeEach
    void setUp() {
        service = new MessageTemplateService(templateRepository, clientRepository, planService);
        when(templateRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(vieja, bienvenidaOro, whatsappDiamante, general));
        when(templateRepository.save(any(MessageTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planService.requireExisting(anyString())).thenAnswer(inv -> {
            String valor = ((String) inv.getArgument(0)).trim();
            for (String plan : List.of("Platinum", "Oro", "Diamante")) {
                if (plan.equalsIgnoreCase(valor)) return plan;
            }
            throw new IllegalArgumentException("Plan inválido: " + valor);
        });
    }

    @Test
    @DisplayName("Sin filtros lista todas, primero las activas")
    void listaTodasConActivasPrimero() {
        assertThat(service.list(null, null)).extracting(TemplateResponse::name)
                .containsExactly("Bienvenida Oro", "Oferta", "Recordatorio", "Antigua");
    }

    @Test
    @DisplayName("Filtrar por plan incluye las generales; por canal no distingue mayusculas")
    void filtraPorCanalYPlan() {
        assertThat(service.list("email", "oro")).extracting(TemplateResponse::name)
                .containsExactly("Bienvenida Oro", "Recordatorio", "Antigua");
        assertThat(service.list("WHATSAPP", null)).extracting(TemplateResponse::name)
                .containsExactly("Oferta");
    }

    @Test
    @DisplayName("Un canal desconocido se rechaza con los valores permitidos")
    void canalDesconocidoSeRechaza() {
        assertThatThrownBy(() -> service.list("sms", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[EMAIL, WHATSAPP, IN_APP]");
    }

    @Test
    @DisplayName("Crear una plantilla la deja activa con el plan del catalogo")
    void creaPlantilla() {
        TemplateResponse creada = service.create(new TemplateRequest(" Seguimiento ", "email", "oro",
                " Hola {nombre} ", "Tu plan {plan}\n"));

        assertThat(creada.templateId()).isNotNull();
        assertThat(creada.name()).isEqualTo("Seguimiento");
        assertThat(creada.channel()).isEqualTo("EMAIL");
        assertThat(creada.plan()).isEqualTo("Oro");
        assertThat(creada.subject()).isEqualTo("Hola {nombre}");
        assertThat(creada.body()).isEqualTo("Tu plan {plan}");
        assertThat(creada.active()).isTrue();
    }

    @Test
    @DisplayName("Nombre, canal y cuerpo son obligatorios; el plan debe existir")
    void validaCamposObligatorios() {
        assertThatThrownBy(() -> service.create(new TemplateRequest(" ", "EMAIL", null, null, "Hola")))
                .hasMessage("El nombre de la plantilla es obligatorio");
        assertThatThrownBy(() -> service.create(new TemplateRequest("A", null, null, null, "Hola")))
                .hasMessageContaining("El canal es obligatorio");
        assertThatThrownBy(() -> service.create(new TemplateRequest("A", "EMAIL", null, null, "  ")))
                .hasMessage("El cuerpo de la plantilla es obligatorio");
        assertThatThrownBy(() -> service.create(new TemplateRequest("A", "EMAIL", "Bronce", null, "Hola")))
                .hasMessageContaining("Plan inválido: Bronce");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Editar reemplaza los datos y conserva el id y la fecha de creacion")
    void editaPlantilla() {
        when(templateRepository.findById(general.getTemplateId())).thenReturn(Optional.of(general));
        LocalDateTime creada = general.getCreatedAt();

        TemplateResponse editada = service.update(general.getTemplateId(),
                new TemplateRequest("Recordatorio IN APP", "IN_APP", null, null, "Te escribimos, {nombre}"));

        assertThat(editada.templateId()).isEqualTo(general.getTemplateId());
        assertThat(editada.channel()).isEqualTo("IN_APP");
        assertThat(editada.subject()).isNull();
        assertThat(editada.createdAt()).isEqualTo(creada);
    }

    @Test
    @DisplayName("Desactivar y reactivar una plantilla")
    void activaYDesactiva() {
        when(templateRepository.findById(general.getTemplateId())).thenReturn(Optional.of(general));

        assertThat(service.setActive(general.getTemplateId(), false).active()).isFalse();
        assertThat(service.setActive(general.getTemplateId(), true).active()).isTrue();
        assertThatThrownBy(() -> service.setActive(general.getTemplateId(), null))
                .hasMessage("El campo active es obligatorio");
    }

    @Test
    @DisplayName("La vista previa reemplaza las variables con los datos del cliente")
    void vistaPrevia() {
        MessageTemplate plantilla = plantilla("Oro", MessageTemplate.Channel.EMAIL, "Oro", true);
        plantilla.setSubject("{nombre}, tu plan {plan}");
        plantilla.setBody("Hola {nombre} de {empresa}. {otra}");
        when(templateRepository.findById(plantilla.getTemplateId())).thenReturn(Optional.of(plantilla));
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(
                Client.builder().clientId("c-1").additionalInfo("Muebles Prim").plan("Oro").build()));

        TemplatePreviewResponse vista = service.preview(plantilla.getTemplateId(), "c-1");

        assertThat(vista.subject()).isEqualTo("Muebles Prim, tu plan Oro");
        assertThat(vista.body()).isEqualTo("Hola Muebles Prim de Muebles Prim. {otra}");
    }

    @Test
    @DisplayName("La vista previa exige un cliente que exista")
    void vistaPreviaSinCliente() {
        when(templateRepository.findById(general.getTemplateId())).thenReturn(Optional.of(general));
        when(clientRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(general.getTemplateId(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(general.getTemplateId(), "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("requireActive rechaza plantillas inactivas o inexistentes")
    void requireActive() {
        UUID inexistente = UUID.randomUUID();
        when(templateRepository.findById(vieja.getTemplateId())).thenReturn(Optional.of(vieja));
        when(templateRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActive(vieja.getTemplateId()))
                .hasMessageContaining("inactiva");
        assertThatThrownBy(() -> service.requireActive(inexistente))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static MessageTemplate plantilla(String nombre, MessageTemplate.Channel canal, String plan, boolean activa) {
        return MessageTemplate.builder().templateId(UUID.randomUUID()).name(nombre).channel(canal)
                .plan(plan).subject("Asunto").body("Cuerpo").active(activa)
                .createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
    }
}
