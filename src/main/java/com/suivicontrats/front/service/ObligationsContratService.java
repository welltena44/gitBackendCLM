package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ObligationsContrat;
import com.suivicontrats.front.repository.ObligationsContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObligationsContratService {
    private final ObligationsContratRepository repository;
    private final ContratService contratService;

    public List<ObligationsContrat> byContract(Integer contractId) {
        contratService.getById(contractId);
        return repository.findByContratIdOrderByDateCreationDesc(contractId);
    }

    @Transactional
    public ObligationsContrat create(Integer contractId, ObligationsContrat details) {
        var contract = contratService.getById(contractId);
        Authentication auth = currentAuth();
        requireManagerOrAdmin(auth);
        if (details.getTitre() == null || details.getTitre().isBlank())
            throw new IllegalArgumentException("Le titre de l’obligation est obligatoire.");
        ObligationsContrat obligation = ObligationsContrat.builder()
                .contratId(contractId)
                .titre(details.getTitre().trim())
                .description(details.getDescription())
                .echeance(details.getEcheance())
                .responsableId(details.getResponsableId() == null ? contract.getResponsableId() : details.getResponsableId())
                .creePar((Integer) auth.getPrincipal())
                .statut("EN_COURS")
                .build();
        return repository.save(obligation);
    }

    @Transactional
    public ObligationsContrat close(Long id) {
        ObligationsContrat obligation = getManageable(id);
        if ("ANNULEE".equals(obligation.getStatut()))
            throw new IllegalStateException("Une obligation annulée ne peut pas être clôturée.");
        obligation.setStatut("TERMINEE");
        obligation.setDateCloture(LocalDateTime.now());
        return repository.save(obligation);
    }

    @Transactional
    public ObligationsContrat cancel(Long id) {
        ObligationsContrat obligation = getManageable(id);
        if ("TERMINEE".equals(obligation.getStatut()))
            throw new IllegalStateException("Une obligation terminée ne peut pas être annulée.");
        obligation.setStatut("ANNULEE");
        obligation.setDateCloture(LocalDateTime.now());
        return repository.save(obligation);
    }

    public boolean hasOpenObligations(Integer contractId) {
        return repository.existsByContratIdAndStatut(contractId, "EN_COURS");
    }

    private ObligationsContrat getManageable(Long id) {
        ObligationsContrat obligation = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Obligation non trouvée"));
        contratService.getById(obligation.getContratId());
        requireManagerOrAdmin(currentAuth());
        return obligation;
    }

    private void requireManagerOrAdmin(Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_GESTIONNAIRE")))
            throw new AccessDeniedException("Gestion réservée au gestionnaire du contrat ou à l’administrateur.");
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer))
            throw new AccessDeniedException("Authentification requise");
        return auth;
    }
}
