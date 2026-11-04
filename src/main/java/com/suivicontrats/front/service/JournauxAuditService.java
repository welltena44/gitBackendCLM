package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.JournauxAudit;
import com.suivicontrats.front.repository.JournauxAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JournauxAuditService {

    private final JournauxAuditRepository repo;

    public List<JournauxAudit> all() {
        return repo.findAllByOrderByDateActionDesc();
    }

    public Page<JournauxAudit> search(Integer userId, String action, LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw new IllegalArgumentException("La date de début doit précéder la date de fin.");
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        return repo.search(userId, normalizedAction, fromDate, toDate, pageable);
    }

    public JournauxAudit getOne(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Entrée d'audit non trouvée"));
    }

    public List<JournauxAudit> byContrat(Integer contratId) {
        return repo.findByContratIdOrderByDateActionDesc(contratId);
    }

    public List<JournauxAudit> byUser(Integer userId) {
        return repo.findByUtilisateurIdOrderByDateActionDesc(userId);
    }

    public JournauxAudit record(Integer userId, Integer contractId, String contractReference,
            String action, String oldValue, String newValue) {
        return repo.save(JournauxAudit.builder()
                .utilisateurId(userId)
                .contratId(contractId)
                .referenceContrat(contractReference)
                .action(action)
                .ancienneValeur(oldValue)
                .nouvelleValeur(newValue)
                .build());
    }

    public JournauxAudit save(JournauxAudit j) {
        j.setId(null);
        return repo.save(j);
    }

    public JournauxAudit update(Long id, JournauxAudit details) {
        JournauxAudit journal = getOne(id);
        journal.setUtilisateurId(details.getUtilisateurId());
        journal.setContratId(details.getContratId());
        journal.setReferenceContrat(details.getReferenceContrat());
        journal.setAction(details.getAction());
        journal.setAncienneValeur(details.getAncienneValeur());
        journal.setNouvelleValeur(details.getNouvelleValeur());
        return repo.save(journal);
    }

    public void delete(Long id) {
        repo.delete(getOne(id));
    }
}
