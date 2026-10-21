package com.cimaxis.demo.marketing.service.campaigns;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalRequest;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalResponse;
import com.cimaxis.demo.marketing.mapper.campaigns.ProposalMapper;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;

@Service
public class ProposalService {

    private static final int LONGITUD_PROJECT_ID = 36;

    private final ProposalRepository proposalRepository;
    private final ClientRepository clientRepository;
    private final ProposalMapper proposalMapper;
    private final ProposalSentNotifier sentNotifier;

    public ProposalService(ProposalRepository proposalRepository,
                           ClientRepository clientRepository,
                           ProposalMapper proposalMapper,
                           ProposalSentNotifier sentNotifier) {
        this.proposalRepository = proposalRepository;
        this.clientRepository = clientRepository;
        this.proposalMapper = proposalMapper;
        this.sentNotifier = sentNotifier;
    }

    public List<ProposalResponse> findAll() {
        return proposalMapper.toResponseList(proposalRepository.findAll());
    }

    public ProposalResponse findById(Integer id) {
        return proposalMapper.toResponse(requireProposal(id));
    }

    public List<ProposalResponse> findByClient(String clientId) {
        return proposalMapper.toResponseList(proposalRepository.findByClientId(clientId));
    }

    /** El estado llega como texto desde la URL; el mapper valida y traduce. */
    public List<ProposalResponse> findByStatus(String status) {
        return proposalMapper.toResponseList(
                proposalRepository.findByStatus(proposalMapper.parseStatus(status)));
    }

    /**
     * Flujo principal. Si el cliente no existe se corta la operacion.
     */
    @Transactional
    public ProposalResponse create(ProposalRequest request) {
        if (request.getClientId() == null || !clientRepository.existsById(request.getClientId())) {
            throw new IllegalArgumentException(
                    "No es posible crear la propuesta: el cliente no existe en el CRM");
        }

        Proposal proposal = proposalMapper.toEntity(request);
        if (proposal.getStatus() == null) {
            proposal.setStatus(Proposal.ProposalStatus.In_diagnosis);
        }
        if (proposal.getCreatedDate() == null) {
            proposal.setCreatedDate(LocalDate.now());
        }
        proposal.setCreatedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        Proposal guardada = proposalRepository.save(proposal);
        avisarSiPasaAEnviada(null, guardada);
        return proposalMapper.toResponse(guardada);
    }

    @Transactional
    public ProposalResponse update(Integer id, ProposalRequest request) {
        Proposal existing = requireProposal(id);
        Proposal.ProposalStatus anterior = existing.getStatus();
        proposalMapper.aplicarCambios(request, existing);
        existing.setUpdatedAt(LocalDateTime.now());
        Proposal guardada = proposalRepository.save(existing);
        avisarSiPasaAEnviada(anterior, guardada);
        return proposalMapper.toResponse(guardada);
    }

    /**
     * Al pasar a un estado terminal se registra la fecha de respuesta, dato que
     * usan tanto el KPI de ingresos como el trigger proposal_no_response.
     */
    @Transactional
    public ProposalResponse changeStatus(Integer id, String status) {
        Proposal.ProposalStatus nuevo = proposalMapper.parseStatus(status);
        if (nuevo == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        Proposal existing = requireProposal(id);
        Proposal.ProposalStatus anterior = existing.getStatus();
        existing.setStatus(nuevo);
        if (nuevo == Proposal.ProposalStatus.Approved || nuevo == Proposal.ProposalStatus.Rejected) {
            existing.setResponseDate(LocalDate.now());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        Proposal guardada = proposalRepository.save(existing);
        avisarSiPasaAEnviada(anterior, guardada);
        return proposalMapper.toResponse(guardada);
    }

    /**
     * Vincula la propuesta con el proyecto de CIMAxios que se creo a partir de ella.
     * Solo aplica a propuestas aprobadas; projectId null (o vacio) desvincula.
     */
    @Transactional
    public ProposalResponse linkProject(Integer id, String projectId) {
        Proposal existing = requireProposal(id);
        if (existing.getStatus() != Proposal.ProposalStatus.Approved) {
            throw new IllegalArgumentException(
                    "Solo se puede vincular un proyecto a una propuesta aprobada. Estado actual: "
                    + (existing.getStatus() != null ? existing.getStatus().name() : "sin estado"));
        }
        String valor = projectId == null || projectId.isBlank() ? null : projectId.trim();
        if (valor != null && valor.length() > LONGITUD_PROJECT_ID) {
            throw new IllegalArgumentException(
                    "El identificador del proyecto admite máximo " + LONGITUD_PROJECT_ID + " caracteres");
        }
        existing.setProjectId(valor);
        existing.setUpdatedAt(LocalDateTime.now());
        return proposalMapper.toResponse(proposalRepository.save(existing));
    }

    @Transactional
    public void delete(Integer id) {
        if (!proposalRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Propuesta", id);
        }
        proposalRepository.deleteById(id);
    }

    /** Aviso por correo al cliente cuando la propuesta pasa a Sent (no se repite si ya estaba en Sent). */
    private void avisarSiPasaAEnviada(Proposal.ProposalStatus anterior, Proposal guardada) {
        if (sentNotifier != null && guardada != null
                && guardada.getStatus() == Proposal.ProposalStatus.Sent
                && anterior != Proposal.ProposalStatus.Sent) {
            sentNotifier.notifySent(guardada);
        }
    }

    private Proposal requireProposal(Integer id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Propuesta", id));
    }
}
