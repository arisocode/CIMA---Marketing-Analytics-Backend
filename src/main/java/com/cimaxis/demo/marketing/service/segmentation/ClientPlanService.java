package com.cimaxis.demo.marketing.service.segmentation;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Gestion del plan comercial de los clientes.
 */
@Service
@RequiredArgsConstructor
public class ClientPlanService {

    private final ClientRepository clientRepository;

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    public Client findById(String clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.de("Cliente", clientId));
    }

    public List<Client> findByPlan(String plan) {
        return clientRepository.findByPlan(parsePlan(plan));
    }

    /** Clientes sincronizados que aun no tienen plan asignado. */
    public List<Client> findSinPlan() {
        return clientRepository.findAll().stream()
                .filter(c -> c.getPlan() == null)
                .toList();
    }

    @Transactional
    public Client assignPlan(String clientId, String plan) {
        Client client = findById(clientId);
        client.setPlan(plan == null || plan.isBlank() ? null : parsePlan(plan));
        client.setUpdatedAt(LocalDateTime.now());
        return clientRepository.save(client);
    }

    /**
     * Asignacion masiva.
     */
    @Transactional
    public Map<String, Object> assignPlanBulk(Map<String, String> asignaciones) {
        List<String> actualizados = new java.util.ArrayList<>();
        Map<String, String> errores = new LinkedHashMap<>();

        asignaciones.forEach((clientId, plan) -> {
            try {
                assignPlan(clientId, plan);
                actualizados.add(clientId);
            } catch (Exception e) {
                errores.put(clientId, e.getMessage());
            }
        });

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("actualizados", actualizados.size());
        resultado.put("clientes", actualizados);
        if (!errores.isEmpty()) {
            resultado.put("errores", errores);
        }
        return resultado;
    }

    private Client.Plan parsePlan(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El plan es obligatorio");
        }
        for (Client.Plan plan : Client.Plan.values()) {
            if (plan.name().equalsIgnoreCase(valor.trim())) {
                return plan;
            }
        }
        throw new IllegalArgumentException(
                "Plan invalido: " + valor + ". Valores permitidos: Oro, Esmeralda, Premium");
    }
}
