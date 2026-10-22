package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.repository.AvenantsRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.VersionsContrat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AvenantsService {

    private final AvenantsRepository repo;
    private final ContratService contratService;
    private final VersionsContratRepository versionsRepo;
    private final ContratRepository contratsRepo;
    private final JournauxAuditService audit;
    private final NotificationsService notifications;

    public List<Avenants> all(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(a -> allowed.contains(a.getContratId())).toList();
    }
    public List<Avenants> byContrat(Integer contratId){ contratService.getById(contratId); return repo.findByContratIdOrderByNumeroAsc(contratId); }
    public Avenants getOne(Long id){ Avenants a = repo.findById(id).orElseThrow(() -> new RuntimeException("Avenant non trouvé")); contratService.getById(a.getContratId()); return a; }
    @Transactional
    public Avenants save(Avenants a){
        if (!canManage()) throw new AccessDeniedException("Seul le gestionnaire du contrat peut créer un avenant.");
        Contrat contract = contratService.getById(a.getContratId()); a.setId(null);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        a.setCreePar((Integer) auth.getPrincipal());
        if (a.getNumero() == null || a.getNumero() <= 0 || a.getDateAvenant() == null || a.getDescription() == null || a.getDescription().isBlank())
            throw new IllegalArgumentException("Numéro, date et description de l’avenant sont obligatoires.");
        a.setNumero(repo.findTopByContratIdOrderByNumeroDesc(contract.getId()).map(old -> old.getNumero() + 1).orElse(1));
        if (a.getMontantApres() != null && a.getMontantApres().signum() <= 0)
            throw new IllegalArgumentException("Le montant après avenant doit être positif.");
        if (a.getMontantApres() == null && a.getDateFinApres() == null)
            throw new IllegalArgumentException("L’avenant doit modifier le montant ou la date de fin du contrat.");
        BigDecimal oldAmount = contract.getMontant();
        java.time.LocalDate oldEnd = contract.getDateFin();
        int nextVersion = versionsRepo.findTopByContratIdOrderByVersionDesc(contract.getId()).map(v -> v.getVersion() + 1).orElse(1);
        VersionsContrat version = versionsRepo.save(VersionsContrat.builder().contratId(contract.getId()).version(nextVersion)
                .contenu(snapshot(contract)).creePar((Integer) auth.getPrincipal()).build());
        String before = summary(contract);
        a.setMontantAvant(oldAmount);
        a.setDateFinAvant(oldEnd);
        if (a.getMontantApres() != null) contract.setMontant(a.getMontantApres());
        if (a.getDateFinApres() != null) {
            contract.setDateFin(a.getDateFinApres());
            if (contract.getDateDebut() != null) contract.setDureeMois((int) java.time.temporal.ChronoUnit.MONTHS.between(contract.getDateDebut(), a.getDateFinApres()));
        }
        if (contract.getDateDebut() != null && contract.getDateFin() != null && !contract.getDateFin().isAfter(contract.getDateDebut()))
            throw new IllegalArgumentException("La nouvelle date de fin doit être postérieure à la date de début.");
        Contrat updated = contratsRepo.save(contract);
        Avenants saved = repo.save(a);
        version.setAvenantId(saved.getId());
        versionsRepo.save(version);
        audit.record((Integer) auth.getPrincipal(), contract.getId(), contract.getReference(), "AVENANT_CREE", before, summary(updated));
        notifications.notifyUser(contract.getResponsableId(), contract.getId(), "Avenant enregistré", "L’avenant n°" + saved.getNumero() + " a modifié le contrat " + contract.getReference() + ". Une version précédente a été archivée.", "AVENANT");
        return saved;
    }

    private java.util.Map<String, Object> snapshot(Contrat c) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("reference", c.getReference()); result.put("objet", c.getObjet());
        result.put("description", c.getDescription()); result.put("clauses", c.getClauses());
        result.put("montant", c.getMontant()); result.put("devise", c.getDevise());
        result.put("dateSignature", c.getDateSignature()); result.put("dateDebut", c.getDateDebut());
        result.put("dateFin", c.getDateFin()); result.put("dureeMois", c.getDureeMois());
        result.put("service", c.getService()); result.put("renouvellementAutomatique", c.getRenouvellementAutomatique());
        result.put("joursPreavis", c.getJoursPreavis()); result.put("typeId", c.getTypeId());
        result.put("statut", c.getStatut());
        return result;
    }
    private String summary(Contrat c) { return "montant=" + c.getMontant() + "; dateFin=" + c.getDateFin() + "; statut=" + c.getStatut(); }
    public Avenants update(Long id, Avenants details){
        getOne(id);
        throw new AccessDeniedException("Un avenant archivé est immuable. Créez un nouvel avenant correctif.");
    }
    public void delete(Long id){ getOne(id); throw new AccessDeniedException("Un avenant archivé ne peut pas être supprimé afin de préserver la traçabilité."); }

    private boolean canManage(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_GESTIONNAIRE"));
    }
}
