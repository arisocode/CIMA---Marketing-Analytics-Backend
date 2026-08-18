package com.cimaxis.demo.integration.crm.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.integration.crm.CrmAuthClient;

/**
 * Sincronizacion de clientes y proyectos desde el CRM base hacia el esquema de
 * marketing.
 */
@Service
public class CrmSyncService {

    private static final Logger log = LoggerFactory.getLogger(CrmSyncService.class);

    private final CrmIntegrationService crmIntegrationService;
    private final CrmAuthClient crmAuthClient;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    @Value("${cimaxis.crm.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${cimaxis.crm.sync.on-startup:true}")
    private boolean syncOnStartup;

    @Value("${cimaxis.crm.service-account.email:}")
    private String serviceEmail;

    @Value("${cimaxis.crm.service-account.password:}")
    private String servicePassword;

    public CrmSyncService(CrmIntegrationService crmIntegrationService,
                          CrmAuthClient crmAuthClient,
                          ClientRepository clientRepository,
                          ProjectRepository projectRepository) {
        this.crmIntegrationService = crmIntegrationService;
        this.crmAuthClient = crmAuthClient;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    // ------------------------------------------------------------------
    // Disparadores
    // ------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void syncAlArrancar() {
        if (!syncEnabled || !syncOnStartup) {
            return;
        }
        try {
            Map<String, Object> resultado = syncAll(null);
            log.info("Sincronizacion inicial con el CRM completada: {}", resultado);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar con el CRM al arrancar: {}. "
                    + "La aplicacion continua; use POST /api/v1/integration/sync "
                    + "cuando el CRM este disponible.", e.getMessage());
        }
    }

    /** Sincronizacion periodica. Por defecto cada seis horas. */
    @Scheduled(cron = "${cimaxis.crm.sync.cron:0 0 */6 * * *}")
    public void syncProgramado() {
        if (!syncEnabled) {
            return;
        }
        try {
            log.info("Sincronizacion programada con el CRM: {}", syncAll(null));
        } catch (Exception e) {
            log.error("Fallo la sincronizacion programada con el CRM: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Operacion principal
    // ------------------------------------------------------------------

    public Map<String, Object> syncAll(String bearerToken) {
        String token = resolverToken(bearerToken);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("clientesSincronizados", syncClients(token));
        resultado.put("proyectosSincronizados", syncProjects(token));
        resultado.put("fecha", LocalDateTime.now());
        return resultado;
    }

    @Transactional
    public int syncClients(String token) {
        List<Map<String, Object>> remotos = crmIntegrationService.getClients(token);
        int procesados = 0;

        for (Map<String, Object> remoto : remotos) {
            String clientId = crmIntegrationService.extractClientId(remoto);
            if (clientId == null || clientId.isBlank()) {
                continue;
            }

            Client client = clientRepository.findById(clientId).orElseGet(() -> {
                Client nuevo = new Client();
                nuevo.setClientId(clientId);
                // created_at solo se fija la primera vez: es la base del KPI
                // de clientes nuevos por periodo y no debe moverse.
                nuevo.setCreatedAt(extraerFecha(remoto, "created_at", "createdAt"));
                return nuevo;
            });

            // user_id es NOT NULL en el modelo. En el CRM el cliente ES un
            // usuario, de modo que ambos identificadores coinciden salvo que el
            // CRM exponga uno distinto.
            String userId = primerValor(remoto, "user_id", "userId");
            client.setUserId(userId != null ? userId : clientId);

            String email = crmIntegrationService.extractClientEmail(remoto);
            if (email != null) {
                client.setContactInfo(email);
            }

            String direccion = primerValor(remoto, "address", "direccion");
            if (direccion != null) {
                client.setAddress(direccion);
            }

            if (client.getAdditionalInfo() == null) {
                client.setAdditionalInfo(crmIntegrationService.extractClientName(remoto));
            }

            if (client.getCreatedAt() == null) {
                client.setCreatedAt(LocalDateTime.now());
            }
            client.setUpdatedAt(LocalDateTime.now());

            // El plan es un atributo comercial propio de marketing: el CRM no lo
            // conoce, asi que solo se asigna si aun no tiene valor.
            if (client.getPlan() == null) {
                Client.Plan plan = parsePlan(primerValor(remoto, "plan", "plan_type"));
                client.setPlan(plan);
            }

            clientRepository.save(client);
            procesados++;
        }

        log.info("Clientes sincronizados desde el CRM: {}", procesados);
        return procesados;
    }

    @Transactional
    public int syncProjects(String token) {
        List<Map<String, Object>> remotos;
        try {
            remotos = crmIntegrationService.getProjects(token);
        } catch (Exception e) {
            log.warn("No se pudieron sincronizar proyectos: {}", e.getMessage());
            return 0;
        }

        int procesados = 0;
        for (Map<String, Object> remoto : remotos) {
            String projectId = primerValor(remoto, "id", "project_id", "projectId");
            String clientId = primerValor(remoto, "client_id", "clientId");

            // Sin cliente asociado el proyecto no sirve para los indicadores y
            // ademas violaria la restriccion NOT NULL del modelo.
            if (projectId == null || clientId == null) {
                continue;
            }

            final String idProyecto = projectId;
            Project project = projectRepository.findById(idProyecto).orElseGet(() -> {
                Project nuevo = new Project();
                nuevo.setProjectId(idProyecto);
                nuevo.setCreatedAt(extraerFecha(remoto, "created_at", "createdAt"));
                return nuevo;
            });

            project.setClientId(clientId);

            String nombre = primerValor(remoto, "name", "project_name", "projectName", "title");
            project.setProjectName(nombre != null ? nombre : "Proyecto " + idProyecto);

            project.setDescription(primerValor(remoto, "description", "descripcion"));
            project.setStatus(primerValor(remoto, "status", "estado"));

            if (project.getCreatedAt() == null) {
                project.setCreatedAt(LocalDateTime.now());
            }

            // updated_at determina en que periodo se contabiliza un proyecto
            // cerrado, por eso se toma del CRM cuando esta disponible.
            LocalDateTime actualizado = extraerFecha(remoto, "updated_at", "updatedAt");
            project.setUpdatedAt(actualizado != null ? actualizado : LocalDateTime.now());

            projectRepository.save(project);
            procesados++;
        }

        log.info("Proyectos sincronizados desde el CRM: {}", procesados);
        return procesados;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private String resolverToken(String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            return bearerToken;
        }
        if (serviceEmail == null || serviceEmail.isBlank()
                || servicePassword == null || servicePassword.isBlank()) {
            throw new IllegalStateException(
                    "No hay token disponible ni cuenta de servicio configurada. "
                            + "Defina cimaxis.crm.service-account.email y .password, "
                            + "o invoque la sincronizacion con un token de usuario.");
        }
        return crmAuthClient.login(serviceEmail, servicePassword);
    }

    private String primerValor(Map<String, Object> origen, String... claves) {
        for (String clave : claves) {
            Object valor = origen.get(clave);
            if (valor != null && !valor.toString().isBlank()) {
                return valor.toString();
            }
        }
        return null;
    }

    private LocalDateTime extraerFecha(Map<String, Object> origen, String... claves) {
        String valor = primerValor(origen, claves);
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).toLocalDateTime();
        } catch (Exception ignorado) {
            // continua con el siguiente formato
        }
        try {
            return LocalDateTime.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignorado) {
            log.debug("Formato de fecha no reconocido: {}", valor);
            return null;
        }
    }

    private Client.Plan parsePlan(String valor) {
        if (valor == null) {
            return null;
        }
        for (Client.Plan plan : Client.Plan.values()) {
            if (plan.name().equalsIgnoreCase(valor.trim())) {
                return plan;
            }
        }
        return null;
    }
}
