package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.repository.AvenantsRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestion du cycle de vie des avenants.
 * Un avenant est d'abord une proposition : le contrat n'est modifié qu'après
 * approbation de toutes les étapes du circuit calculé.
 */
@Service
@RequiredArgsConstructor
public class AvenantsService {

    private final AvenantsRepository repo;
    private final ContratService contratService;
    private final JournauxAuditService audit;
    private final NotificationsService notifications;
    private final ReglesValidationService reglesValidationService;
    private final ValidationsRepository validationsRepo;

    public List<Avenants> all() {
        var allowed = contratService.getAll().stream().map(Contrat::getId)
                .collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(a -> allowed.contains(a.getContratId())).toList();
    }

    public List<Avenants> byContrat(Integer contratId) {
        contratService.getById(contratId);
        return repo.findByContratIdOrderByNumeroAsc(contratId);
    }

    public Avenants getOne(Long id) {
        Avenants avenant = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avenant non trouvé."));
        contratService.getById(avenant.getContratId());
        return avenant;
    }

    /** Crée l'avenant et génère immédiatement son circuit de validation. */
    @Transactional
    public Avenants save(Avenants demande) {
        Authentication auth = currentAuth();
        Integer userId = (Integer) auth.getPrincipal();
        if (!hasRole(auth, "GESTIONNAIRE") && !hasRole(auth, "ADMIN")) {
            throw new AccessDeniedException("Seul le gestionnaire autorisé peut créer un avenant.");
        }

        Contrat contrat = contratService.getById(demande.getContratId());
        if (!hasRole(auth, "ADMIN") && !userId.equals(contrat.getResponsableId())) {
            throw new AccessDeniedException("Ce contrat ne relève pas de votre périmètre.");
        }
        if (demande.getDateAvenant() == null || demande.getDescription() == null
                || demande.getDescription().isBlank()) {
            throw new IllegalArgumentException("La date et la description de l'avenant sont obligatoires.");
        }
        if (demande.getMontantApres() == null && demande.getDateFinApres() == null) {
            throw new IllegalArgumentException("L'avenant doit modifier le montant ou la date de fin du contrat.");
        }
        if (demande.getMontantApres() != null && demande.getMontantApres().signum() <= 0) {
            throw new IllegalArgumentException("Le montant après avenant doit être strictement positif.");
        }
        if (demande.getDateFinApres() != null && !demande.getDateFinApres().isAfter(contrat.getDateDebut())) {
            throw new IllegalArgumentException("La nouvelle date de fin doit être postérieure à la date de début.");
        }

        BigDecimal montantAvant = contrat.getMontant();
        BigDecimal montantApres = demande.getMontantApres() == null ? montantAvant : demande.getMontantApres();
        List<ReglesValidation> circuit = reglesValidationService.getCircuit(contrat.getTypeId(), montantApres);
        if (circuit.isEmpty()) {
            String message = "Aucune règle de validation ne couvre le montant et le type de l'avenant du contrat "
                    + contrat.getReference() + ". L'avenant n'a pas été soumis.";
            notifications.notifyMissingRuleForContract(contrat.getId(), "Circuit d'avenant introuvable", message);
            throw new IllegalArgumentException(message);
        }

        demande.setId(null);
        demande.setNumero(repo.findTopByContratIdOrderByNumeroDesc(contrat.getId())
                .map(previous -> previous.getNumero() + 1).orElse(1));
        demande.setCreePar(userId);
        demande.setMontantAvant(montantAvant);
        demande.setDateFinAvant(contrat.getDateFin());
        demande.setStatut("EN_VALIDATION");
        demande.setCycleValidation(1);
        Avenants saved = repo.save(demande);

        List<Validations> steps = construireEtapes(saved, contrat, circuit, userId, 1);
        validationsRepo.saveAll(steps);
        notifierPremiereEtape(saved, contrat, steps);

        audit.record(userId, contrat.getId(), contrat.getReference(), "AVENANT_SOUMIS_VALIDATION",
                "statut=INEXISTANT", "avenant=" + saved.getId() + "; statut=EN_VALIDATION; cycle=1");
        notifications.notifyUserByEmail(contrat.getResponsableId(), contrat.getId(),
                "Avenant soumis", "L'avenant n°" + saved.getNumero() + " du contrat " + contrat.getReference()
                        + " a été soumis au circuit d'approbation.", "AVENANT");
        return saved;
    }

    /** Reconstruit le circuit après correction d'un avenant retourné au gestionnaire. */
    @Transactional
    public Avenants resoumettre(Long id) {
        Authentication auth = currentAuth();
        Avenants avenant = getOne(id);
        if (!"MODIFICATION_DEMANDEE".equals(avenant.getStatut())) {
            throw new IllegalArgumentException("Seul un avenant retourné pour modification peut être resoumis.");
        }
        if (!hasRole(auth, "ADMIN") && !((Integer) auth.getPrincipal()).equals(avenant.getCreePar())) {
            throw new AccessDeniedException("Seul le créateur de l'avenant peut le resoumettre.");
        }
        Contrat contrat = contratService.getById(avenant.getContratId());
        BigDecimal montantCible = avenant.getMontantApres() == null ? contrat.getMontant() : avenant.getMontantApres();
        List<ReglesValidation> circuit = reglesValidationService.getCircuit(contrat.getTypeId(), montantCible);
        if (circuit.isEmpty()) throw new IllegalArgumentException("Aucune règle de validation applicable à cet avenant.");
        int cycle = avenant.getCycleValidation() + 1;
        avenant.setCycleValidation(cycle);
        avenant.setStatut("EN_VALIDATION");
        Avenants saved = repo.save(avenant);
        List<Validations> steps = construireEtapes(saved, contrat, circuit, (Integer) auth.getPrincipal(), cycle);
        validationsRepo.saveAll(steps);
        notifierPremiereEtape(saved, contrat, steps);
        return saved;
    }

    private List<Validations> construireEtapes(Avenants avenant, Contrat contrat,
            List<ReglesValidation> circuit, Integer demandeurId, Integer cycle) {
        int premierNiveau = circuit.stream().map(ReglesValidation::getNiveau).min(Integer::compareTo).orElseThrow();
        List<Validations> resultat = new ArrayList<>();
        for (ReglesValidation regle : circuit) {
            List<String> roles = switch (regle.getRoleRequis()) {
                case "VALIDEUR" -> List.of("VALIDEUR");
                case "JURIDIQUE" -> List.of("JURIDIQUE");
                case "VALIDEUR_ET_JURIDIQUE" -> List.of("VALIDEUR", "JURIDIQUE");
                default -> throw new IllegalArgumentException("Rôle invalide dans la règle de validation : " + regle.getRoleRequis());
            };
            for (String role : roles) {
                resultat.add(Validations.builder()
                        .contratId(contrat.getId()).avenantId(avenant.getId())
                        .demandeurId(demandeurId).niveau(regle.getNiveau()).roleRequis(role)
                        .valideurId("VALIDEUR".equals(role) ? regle.getValideurId() : null)
                        .cycle(cycle)
                        .decision(regle.getNiveau() == premierNiveau ? "EN_ATTENTE" : "A_VENIR")
                        .dateSoumission(LocalDateTime.now()).build());
            }
        }
        return resultat;
    }

    private void notifierPremiereEtape(Avenants avenant, Contrat contrat, List<Validations> steps) {
        int premierNiveau = steps.stream().map(Validations::getNiveau).min(Integer::compareTo).orElseThrow();
        steps.stream().filter(s -> s.getNiveau() == premierNiveau).forEach(step -> {
            String titre = "Avenant à approuver — " + contrat.getReference();
            String message = "L'avenant n°" + avenant.getNumero() + " du contrat " + contrat.getReference()
                    + " attend votre décision (niveau " + step.getNiveau() + ").";
            if (step.getValideurId() != null) {
                notifications.notifyUserByEmail(step.getValideurId(), contrat.getId(), titre, message, "AVENANT");
            } else {
                notifications.notifyRoleByEmail(Utilisateurs.Role.valueOf(step.getRoleRequis()),
                        contrat.getId(), titre, message, "AVENANT");
            }
        });
    }

    /** Modification uniquement après demande explicite de correction, sans toucher au contrat actif. */
    @Transactional
    public Avenants update(Long id, Avenants details) {
        Avenants actuel = getOne(id);
        Authentication auth = currentAuth();
        if (!"MODIFICATION_DEMANDEE".equals(actuel.getStatut()))
            throw new AccessDeniedException("Un avenant soumis ou approuvé est immuable.");
        if (!hasRole(auth, "ADMIN") && !((Integer) auth.getPrincipal()).equals(actuel.getCreePar()))
            throw new AccessDeniedException("Seul le créateur peut corriger cet avenant.");
        if (details.getDescription() == null || details.getDescription().isBlank())
            throw new IllegalArgumentException("La description est obligatoire.");
        Contrat contrat = contratService.getById(actuel.getContratId());
        if (details.getMontantApres() != null && details.getMontantApres().signum() <= 0)
            throw new IllegalArgumentException("Le montant après avenant doit être strictement positif.");
        if (details.getDateFinApres() != null && !details.getDateFinApres().isAfter(contrat.getDateDebut()))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        if (details.getDateAvenant() == null)
            throw new IllegalArgumentException("La date de l'avenant est obligatoire.");
        actuel.setDescription(details.getDescription());
        actuel.setModifications(details.getModifications());
        actuel.setDateAvenant(details.getDateAvenant());
        actuel.setMontantApres(details.getMontantApres());
        actuel.setDateFinApres(details.getDateFinApres());
        actuel.setDocumentId(details.getDocumentId());
        return repo.save(actuel);
    }

    public void delete(Long id) {
        getOne(id);
        throw new AccessDeniedException("Un avenant ne peut pas être supprimé : sa traçabilité doit être conservée.");
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer))
            throw new AccessDeniedException("Authentification requise.");
        return auth;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
