package com.cimaxis.demo.marketing.service.templates;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;
import com.cimaxis.demo.marketing.dto.templates.TemplatePreviewResponse;
import com.cimaxis.demo.marketing.dto.templates.TemplateRequest;
import com.cimaxis.demo.marketing.dto.templates.TemplateResponse;
import com.cimaxis.demo.marketing.repository.templates.MessageTemplateRepository;
import com.cimaxis.demo.marketing.service.plans.MarketingPlanService;

/**
 * Biblioteca de plantillas de mensajes. Las plantillas no se borran: se
 * desactivan. La vista previa usa MessageRenderer, el mismo reemplazo de
 * variables que aplican los workflows al enviar.
 */
@Service
public class MessageTemplateService {

    private static final int LONGITUD_MAXIMA_NOMBRE = 120;
    private static final int LONGITUD_MAXIMA_ASUNTO = 200;

    private final MessageTemplateRepository templateRepository;
    private final ClientRepository clientRepository;
    private final MarketingPlanService planService;

    public MessageTemplateService(MessageTemplateRepository templateRepository,
                                  ClientRepository clientRepository,
                                  MarketingPlanService planService) {
        this.templateRepository = templateRepository;
        this.clientRepository = clientRepository;
        this.planService = planService;
    }

    /**
     * Plantillas filtradas por canal y plan. Con plan, incluye tambien las
     * generales (sin plan), porque aplican a cualquier plan. Primero las activas.
     */
    public List<TemplateResponse> list(String channel, String plan) {
        MessageTemplate.Channel canal = channel == null || channel.isBlank() ? null : parseChannel(channel);
        String clavePlan = plan == null || plan.isBlank() ? null : MarketingPlanService.clave(plan);

        return templateRepository.findAllByOrderByNameAsc().stream()
                .filter(t -> canal == null || t.getChannel() == canal)
                .filter(t -> clavePlan == null || t.getPlan() == null
                        || MarketingPlanService.clave(t.getPlan()).equals(clavePlan))
                .sorted(Comparator.comparing((MessageTemplate t) -> !Boolean.TRUE.equals(t.getActive())))
                .map(MessageTemplateService::toResponse)
                .toList();
    }

    public TemplateResponse findById(UUID templateId) {
        return toResponse(require(templateId));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        MessageTemplate plantilla = MessageTemplate.builder()
                .templateId(UUID.randomUUID())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        aplicar(requerirCuerpo(request), plantilla);
        return toResponse(templateRepository.save(plantilla));
    }

    @Transactional
    public TemplateResponse update(UUID templateId, TemplateRequest request) {
        MessageTemplate plantilla = require(templateId);
        aplicar(requerirCuerpo(request), plantilla);
        return toResponse(templateRepository.save(plantilla));
    }

    @Transactional
    public TemplateResponse setActive(UUID templateId, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("El campo active es obligatorio");
        }
        MessageTemplate plantilla = require(templateId);
        plantilla.setActive(active);
        return toResponse(templateRepository.save(plantilla));
    }

    /** Asunto y cuerpo con las variables del cliente indicado ({nombre}, {plan}, {empresa}). */
    public TemplatePreviewResponse preview(UUID templateId, String clientId) {
        MessageTemplate plantilla = require(templateId);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("El cliente es obligatorio para la vista previa");
        }
        Client client = clientRepository.findById(clientId.trim())
                .orElseThrow(() -> ResourceNotFoundException.de("Cliente", clientId.trim()));

        var variables = MessageRenderer.variables(client);
        return new TemplatePreviewResponse(
                MessageRenderer.render(plantilla.getSubject(), variables),
                MessageRenderer.render(plantilla.getBody(), variables));
    }

    /** Plantilla activa por id; la usan los workflows al validar y al enviar. */
    public MessageTemplate requireActive(UUID templateId) {
        MessageTemplate plantilla = require(templateId);
        if (!Boolean.TRUE.equals(plantilla.getActive())) {
            throw new IllegalArgumentException("La plantilla " + plantilla.getName() + " está inactiva");
        }
        return plantilla;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void aplicar(TemplateRequest request, MessageTemplate destino) {
        destino.setName(requerirTexto(request.name(), "El nombre de la plantilla es obligatorio",
                LONGITUD_MAXIMA_NOMBRE, "El nombre de la plantilla"));
        if (request.channel() == null || request.channel().isBlank()) {
            throw new IllegalArgumentException("El canal es obligatorio: EMAIL, WHATSAPP o IN_APP");
        }
        destino.setChannel(parseChannel(request.channel()));
        destino.setPlan(request.plan() == null || request.plan().isBlank()
                ? null : planService.requireExisting(request.plan()));
        String asunto = request.subject() == null || request.subject().isBlank() ? null : request.subject().trim();
        if (asunto != null && asunto.length() > LONGITUD_MAXIMA_ASUNTO) {
            throw new IllegalArgumentException("El asunto admite máximo " + LONGITUD_MAXIMA_ASUNTO + " caracteres");
        }
        destino.setSubject(asunto);
        if (request.body() == null || request.body().isBlank()) {
            throw new IllegalArgumentException("El cuerpo de la plantilla es obligatorio");
        }
        destino.setBody(request.body().strip());
    }

    private MessageTemplate require(UUID templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("El identificador de la plantilla es obligatorio");
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> ResourceNotFoundException.de("Plantilla", templateId));
    }

    private static TemplateRequest requerirCuerpo(TemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Los datos de la plantilla son obligatorios");
        }
        return request;
    }

    private static String requerirTexto(String valor, String mensaje, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        String texto = valor.trim();
        if (texto.length() > maximo) {
            throw new IllegalArgumentException(campo + " admite máximo " + maximo + " caracteres");
        }
        return texto;
    }

    static MessageTemplate.Channel parseChannel(String valor) {
        try {
            return MessageTemplate.Channel.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Canal inválido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(MessageTemplate.Channel.values()));
        }
    }

    static TemplateResponse toResponse(MessageTemplate t) {
        return new TemplateResponse(t.getTemplateId(), t.getName(),
                t.getChannel() != null ? t.getChannel().name() : null,
                t.getPlan(), t.getSubject(), t.getBody(), t.getActive(), t.getCreatedAt());
    }
}
