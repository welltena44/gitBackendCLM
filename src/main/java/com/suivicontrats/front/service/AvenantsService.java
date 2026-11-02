
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.VersionsContrat;

import com.suivicontrats.front.repository.AvenantsRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;
import com.suivicontrats.front.repository.ContratRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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

    public List<Avenants> all() {
        var allowed = contratService.getAll()
                .stream()
                .map(Contrat::getId)
                .collect(java.util.stream.Collectors.toSet());

        return repo.findAll()
                .stream()
                .filter(a -> allowed.contains(a.getContratId()))
                .toList();
    }

    public List<Avenants> byContrat(Integer contratId) {
        contratService.getById(contratId);
        return repo.findByContratIdOrderByNumeroAsc(contratId);
    }

    public Avenants getOne(Long id) {
        Avenants a = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Avenant non trouvé"));

        contratService.getById(a.getContratId());

        return a;
    }

    /**
     * Création d'un avenant.
     *
     * Le contrat est d'abord modifié, ensuite l'avenant est enregistré,
     * puis une nouvelle version contenant le nouvel état du contrat
     * est créée.
     */
    @Transactional
    public Avenants save(Avenants a) {

        if (!canManage()) {
            throw new AccessDeniedException(
                    "Seul le gestionnaire du contrat peut créer un avenant."
            );
        }

        Contrat contract = contratService.getById(a.getContratId());

        a.setId(null);

        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();

        Integer utilisateurId = (Integer) auth.getPrincipal();

        a.setCreePar(utilisateurId);

        if (a.getNumero() == null
                || a.getNumero() <= 0
                || a.getDateAvenant() == null
                || a.getDescription() == null
                || a.getDescription().isBlank()) {

            throw new IllegalArgumentException(
                    "Numéro, date et description de l’avenant sont obligatoires."
            );
        }

        // Le numéro est calculé automatiquement.
        a.setNumero(
                repo.findTopByContratIdOrderByNumeroDesc(contract.getId())
                        .map(old -> old.getNumero() + 1)
                        .orElse(1)
        );

        if (a.getMontantApres() != null
                && a.getMontantApres().signum() <= 0) {

            throw new IllegalArgumentException(
                    "Le montant après avenant doit être positif."
            );
        }

        if (a.getMontantApres() == null
                && a.getDateFinApres() == null) {

            throw new IllegalArgumentException(
                    "L’avenant doit modifier le montant ou la date de fin du contrat."
            );
        }

        // Conservation des anciennes valeurs.
        BigDecimal oldAmount = contract.getMontant();
        java.time.LocalDate oldEnd = contract.getDateFin();

        String before = summary(contract);

        a.setMontantAvant(oldAmount);
        a.setDateFinAvant(oldEnd);

        // Application des modifications sur le contrat.
        if (a.getMontantApres() != null) {
            contract.setMontant(a.getMontantApres());
        }

        if (a.getDateFinApres() != null) {
            contract.setDateFin(a.getDateFinApres());

            if (contract.getDateDebut() != null) {
                contract.setDureeMois(
                        (int) java.time.temporal.ChronoUnit.MONTHS.between(
                                contract.getDateDebut(),
                                a.getDateFinApres()
                        )
                );
            }
        }

        if (contract.getDateDebut() != null
                && contract.getDateFin() != null
                && !contract.getDateFin().isAfter(contract.getDateDebut())) {

            throw new IllegalArgumentException(
                    "La nouvelle date de fin doit être postérieure à la date de début."
            );
        }

        // Enregistrement du contrat modifié.
        Contrat updated = contratsRepo.save(contract);

        // Enregistrement de l'avenant.
        Avenants saved = repo.save(a);

        // Création de la nouvelle version APRÈS modification.
        int nextVersion = versionsRepo
                .findTopByContratIdOrderByVersionDesc(contract.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        VersionsContrat version = VersionsContrat.builder()
                .contratId(updated.getId())
                .version(nextVersion)
                .contenu(snapshot(updated))
                .creePar(utilisateurId)
                .avenantId(saved.getId())
                .build();

        versionsRepo.save(version);

        // Journalisation de l'opération.
        audit.record(
                utilisateurId,
                updated.getId(),
                updated.getReference(),
                "AVENANT_CREE",
                before,
                summary(updated)
        );

        // Notification au responsable du contrat.
        notifications.notifyUser(
                updated.getResponsableId(),
                updated.getId(),
                "Avenant enregistré",
                "L’avenant n°" + saved.getNumero()
                        + " a modifié le contrat " + updated.getReference()
                        + ". Une nouvelle version du contrat a été archivée.",
                "AVENANT"
        );

        return saved;
    }

    /**
     * Copie complète des informations contractuelles
     * conservées dans l'historique des versions.
     */
    private Map<String, Object> snapshot(Contrat c) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("reference", c.getReference());
        result.put("objet", c.getObjet());
        result.put("description", c.getDescription());
        result.put("clauses", c.getClauses());
        result.put("montant", c.getMontant());
        result.put("devise", c.getDevise());
        result.put("dateSignature", c.getDateSignature());
        result.put("dateDebut", c.getDateDebut());
        result.put("dateFin", c.getDateFin());
        result.put("dureeMois", c.getDureeMois());
        result.put("service", c.getService());
        result.put("renouvellementAutomatique", c.getRenouvellementAutomatique());
        result.put("joursPreavis", c.getJoursPreavis());
        result.put("typeId", c.getTypeId());
        result.put("statut", c.getStatut());
        result.put("responsableId", c.getResponsableId());

        return result;
    }

    private String summary(Contrat c) {
        return "montant=" + c.getMontant()
                + "; dateFin=" + c.getDateFin()
                + "; statut=" + c.getStatut();
    }

    public Avenants update(Long id, Avenants details) {
        getOne(id);

        throw new AccessDeniedException(
                "Un avenant archivé est immuable. Créez un nouvel avenant correctif."
        );
    }

    public void delete(Long id) {
        getOne(id);

        throw new AccessDeniedException(
                "Un avenant archivé ne peut pas être supprimé afin de préserver la traçabilité."
        );
    }

    private boolean canManage() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();

        return auth != null && auth.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_GESTIONNAIRE")
                );
    }
}
