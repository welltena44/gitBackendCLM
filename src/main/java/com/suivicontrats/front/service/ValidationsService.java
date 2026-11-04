package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.AvenantsRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;
import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.entity.VersionsContrat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationsService {

    private final ValidationsRepository validationsRepo;
    private final ContratRepository contratRepo;
    private final UtilisateursRepository utilisateursRepo;
    private final NotificationsService notificationsService;
    private final JournauxAuditService journauxAuditService;
    private final ReglesValidationService reglesValidationService;
    private final AvenantsRepository avenantsRepo;
    private final VersionsContratRepository versionsRepo;

    public List<Validations> getByContrat(Integer contratId) {
        return all().stream().filter(v -> v.getContratId().equals(contratId)).toList();
    }

    public Validations getOne(Long id) {
        Validations validation = validationsRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));
        if (all().stream().noneMatch(v -> v.getId().equals(id)))
            throw new AccessDeniedException("Cette validation ne relève pas de votre périmètre.");
        return validation;
    }

    public List<Validations> getEnAttente() {
        return validationsRepo.findByDecision("EN_ATTENTE");
    }

    @Transactional
    public List<Validations> genererCircuit(Integer contratId, Integer demandeurId) {
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE"))
            throw new AccessDeniedException("Opération réservée au gestionnaire.");
        Contrat contract = contratRepo.findById(contratId)
                .orElseThrow(() -> new RuntimeException("Contrat non trouvé"));
        if (!hasRole(auth, "ADMIN") && !currentUserId(auth).equals(contract.getResponsableId()))
            throw new AccessDeniedException("Ce contrat ne relève pas de votre périmètre.");
        if (!List.of("BROUILLON", "EN_ATTENTE", "REJETE").contains(contract.getStatut()))
            throw new IllegalArgumentException(
                    "Seul un brouillon, un contrat en attente ou un contrat rejeté peut être soumis en validation.");
        if (validationsRepo.findByContratId(contratId).stream().anyMatch(v -> "EN_ATTENTE".equals(v.getDecision())))
            throw new IllegalArgumentException("Un circuit de validation est déjà en cours.");
        demandeurId = currentUserId(auth);
        Integer cycle = validationsRepo.findTopByContratIdOrderByCycleDesc(contratId)
                .map(previous -> previous.getCycle() + 1)
                .orElse(1);
        List<com.suivicontrats.front.entity.ReglesValidation> circuit = reglesValidationService
                .getCircuit(contract.getTypeId(), contract.getMontant());
        if (circuit.isEmpty()) {
            String message = "Aucune règle active ne couvre le type et le montant du contrat "
                    + contract.getReference() + " (" + contract.getMontant().toPlainString() + " "
                    + contract.getDevise() + "). Le contrat reste en brouillon jusqu'à la configuration d'un seuil.";
            notificationsService.notifyMissingRuleForContract(contratId,
                    "Aucune règle de validation applicable", message);
            throw new IllegalArgumentException(
                    "Aucune règle de validation ne couvre le type et le montant de ce contrat.");
        }
        List<Validations> steps = new java.util.ArrayList<>();
        Integer firstLevel = circuit.stream().map(com.suivicontrats.front.entity.ReglesValidation::getNiveau)
                .min(Integer::compareTo).orElseThrow();
        for (var rule : circuit) {
            List<String> requiredRoles = switch (rule.getRoleRequis()) {
                case "VALIDEUR" -> List.of("VALIDEUR");
                case "JURIDIQUE" -> List.of("JURIDIQUE");
                case "VALIDEUR_ET_JURIDIQUE" -> List.of("VALIDEUR", "JURIDIQUE");
                default -> throw new IllegalArgumentException("Rôle requis invalide dans le circuit.");
            };
            for (String requiredRole : requiredRoles) {
                steps.add(Validations.builder().contratId(contratId).demandeurId(demandeurId)
                        .niveau(rule.getNiveau()).roleRequis(requiredRole)
                        .valideurId("VALIDEUR_ET_JURIDIQUE".equals(rule.getRoleRequis())
                                ? ("VALIDEUR".equals(requiredRole) ? rule.getValideurId() : null)
                                : rule.getValideurId())
                        .cycle(cycle)
                        .decision(rule.getNiveau().equals(firstLevel) ? "EN_ATTENTE" : "A_VENIR")
                        .dateSoumission(LocalDateTime.now()).build());
            }
        }
        List<Validations> saved = validationsRepo.saveAll(steps);
        String ancienStatut = contract.getStatut();
        contract.setStatut("EN_VALIDATION");
        contratRepo.save(contract);

        String reference = contract.getReference() == null ? "#" + contratId : contract.getReference();
        saved.stream().filter(step -> step.getNiveau().equals(firstLevel))
                .forEach(step -> notifierEtape(step, reference));
        journauxAuditService.record(
                demandeurId, contratId, reference, "CONTRAT_SOUMIS_VALIDATION",
                "statut=" + ancienStatut, "statut=EN_VALIDATION; cycle=" + cycle);
        return saved;
    }

    @Transactional
    public Validations decider(Long validationId, Integer valideurId, String decision, String commentaire) {
        Authentication auth = currentAuth();
        if (!valideurId.equals(currentUserId(auth)))
            throw new AccessDeniedException("Décision réservée à l'utilisateur connecté.");

        // Le verrou sur le contrat empêche deux approbations simultanées de perdre
        // la mise à jour du circuit (notamment lorsque Valideur et Juridique agissent ensemble).
        Validations validationPourVerrou = validationsRepo.findById(validationId)
                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));
        contratRepo.findByIdForUpdate(validationPourVerrou.getContratId())
                .orElseThrow(() -> new RuntimeException("Contrat non trouvé"));
        if (!List.of("APPROUVE", "REJETE", "MODIFICATION_DEMANDEE").contains(decision)) {
            throw new IllegalArgumentException("Décision de validation invalide");
        }
        if ("MODIFICATION_DEMANDEE".equals(decision) && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException(
                    "Un commentaire est obligatoire pour justifier la demande de modification.");
        }
        Validations v = getOne(validationId);
        // Les validations d'avenant suivent leur propre cycle et ne modifient
        // le contrat qu'après approbation de toutes les étapes requises.
        if (v.getAvenantId() != null) {
            return deciderAvenant(v, valideurId, decision, commentaire);
        }
        String requiredRole = "ROLE_" + v.getRoleRequis();
        if (!List.of("ROLE_VALIDEUR", "ROLE_JURIDIQUE").contains(requiredRole)
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(requiredRole))
                || !"EN_ATTENTE".equals(v.getDecision())
                || (v.getValideurId() != null && !v.getValideurId().equals(valideurId))) {
            throw new AccessDeniedException("Cette validation ne vous est pas attribuée.");
        }
        v.setValideurId(valideurId);
        v.setDecision(decision);
        v.setCommentaire(commentaire);
        v.setDateValidation(LocalDateTime.now());
        Validations saved = validationsRepo.save(v);
        List<Validations> cycleSteps = validationsRepo.findByContratIdAndCycleOrderByNiveauAsc(v.getContratId(),
                v.getCycle());
        List<Validations> sameLevel = cycleSteps.stream().filter(s -> s.getNiveau().equals(v.getNiveau())).toList();
        boolean levelApproved = sameLevel.stream().allMatch(s -> s.getId().equals(saved.getId())
                ? "APPROUVE".equals(decision)
                : "APPROUVE".equals(s.getDecision()));
        if (!"APPROUVE".equals(decision)) {
            cycleSteps.stream().filter(s -> ("A_VENIR".equals(s.getDecision())
                    || (s.getNiveau().equals(v.getNiveau()) && "EN_ATTENTE".equals(s.getDecision())))
                    && !s.getId().equals(saved.getId()))
                    .forEach(s -> {
                        s.setDecision("NON_REQUISE");
                        validationsRepo.save(s);
                    });
            cycleSteps.stream().filter(s -> "A_VENIR".equals(s.getDecision()))
                    .forEach(s -> {
                        s.setDecision("NON_REQUISE");
                        validationsRepo.save(s);
                    });
        }

        List<Validations> nextLevelSteps = List.of();
        if ("APPROUVE".equals(decision) && levelApproved) {
            Integer nextLevel = cycleSteps.stream()
                    .filter(s -> s.getNiveau() > v.getNiveau() && "A_VENIR".equals(s.getDecision()))
                    .map(Validations::getNiveau).min(Integer::compareTo).orElse(null);
            if (nextLevel != null) {
                nextLevelSteps = cycleSteps.stream().filter(s -> s.getNiveau().equals(nextLevel)
                        && "A_VENIR".equals(s.getDecision())).toList();
                for (Validations next : nextLevelSteps)
                    next.setDecision("EN_ATTENTE");
                validationsRepo.saveAll(nextLevelSteps);
            }
        }
        final List<Validations> activatedSteps = nextLevelSteps;
        final boolean approvalsComplete = "APPROUVE".equals(decision) && levelApproved && activatedSteps.isEmpty();
        final boolean waitingForCoApproval = "APPROUVE".equals(decision) && !levelApproved;
        String reference = contratRepo.findById(v.getContratId()).map(Contrat::getReference)
                .orElse("#" + v.getContratId());
        activatedSteps.forEach(step -> notifierEtape(step, reference));
        contratRepo.findById(v.getContratId()).ifPresent(contract -> {
            String ancienStatut = contract.getStatut();
            if (approvalsComplete)
                contract.setStatut("APPROUVE");
            else if ("APPROUVE".equals(decision))
                contract.setStatut("EN_VALIDATION");
            else if ("REJETE".equals(decision))
                contract.setStatut("REJETE");
            else
                contract.setStatut("BROUILLON");
            contratRepo.save(contract);

            String action = switch (decision) {
                case "APPROUVE" -> "CONTRAT_APPROUVE";
                case "REJETE" -> "CONTRAT_REJETE";
                default -> "MODIFICATION_DEMANDEE";
            };
            String nouvelleValeur = "statut=" + contract.getStatut() + "; decision=" + decision;
            if (commentaire != null && !commentaire.isBlank())
                nouvelleValeur += "; commentaire=" + commentaire.trim();
            journauxAuditService.record(
                    valideurId, contract.getId(), contract.getReference(), action,
                    "statut=" + ancienStatut + "; decision=EN_ATTENTE", nouvelleValeur);

            Integer responsableId = contract.getResponsableId();
            String acteur = "JURIDIQUE".equals(v.getRoleRequis()) ? "Le Juridique" : "Le valideur";
            if (waitingForCoApproval) {
                notificationsService.notifyUser(responsableId, contract.getId(), "Approbation partielle",
                        acteur + " a donné son accord à l'étape " + v.getNiveau() + " du contrat "
                                + contract.getReference()
                                + "; l'autre rôle requis doit encore approuver.",
                        "VALIDATION");
            } else if ("MODIFICATION_DEMANDEE".equals(decision)) {
                String message = acteur + " a demandé des modifications pour le contrat " + contract.getReference()
                        + ". Commentaire : " + commentaire.trim();
                notificationsService.notifyUser(responsableId, contract.getId(), "Modifications demandées", message,
                        "VALIDATION");
            } else if (approvalsComplete) {
                notificationsService.notifyUser(responsableId, contract.getId(), "Contrat approuvé",
                        "Le contrat " + contract.getReference() + " a été approuvé.", "VALIDATION");
            } else if ("REJETE".equals(decision)) {
                String message = "Le contrat " + contract.getReference() + " a été rejeté.";
                if (commentaire != null && !commentaire.isBlank())
                    message += " Commentaire : " + commentaire.trim();
                notificationsService.notifyUser(responsableId, contract.getId(), "Contrat rejeté", message,
                        "VALIDATION");
            }
        });
        return saved;
    }

    /** Traite une décision appartenant au cycle d'un avenant. */
    private Validations deciderAvenant(Validations v, Integer userId, String decision, String commentaire) {
        Avenants avenant = avenantsRepo.findById(v.getAvenantId())
                .orElseThrow(() -> new RuntimeException("Avenant associé introuvable."));
        Contrat contrat = contratRepo.findById(v.getContratId())
                .orElseThrow(() -> new RuntimeException("Contrat associé introuvable."));
        String authority = "ROLE_" + v.getRoleRequis();
        Authentication auth = currentAuth();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority))
                || !"EN_ATTENTE".equals(v.getDecision())
                || (v.getValideurId() != null && !v.getValideurId().equals(userId))) {
            throw new AccessDeniedException("Cette validation d'avenant ne vous est pas attribuée.");
        }

        v.setValideurId(userId);
        v.setDecision(decision);
        v.setCommentaire(commentaire);
        v.setDateValidation(LocalDateTime.now());
        Validations saved = validationsRepo.save(v);
        List<Validations> cycle = validationsRepo.findByAvenantIdAndCycleOrderByNiveauAsc(
                avenant.getId(), v.getCycle());
        List<Validations> niveau = cycle.stream().filter(s -> s.getNiveau().equals(v.getNiveau())).toList();
        boolean niveauApprouve = niveau.stream().allMatch(s -> "APPROUVE".equals(s.getDecision()));

        if (!"APPROUVE".equals(decision)) {
            cycle.stream().filter(s -> !s.getId().equals(saved.getId())
                    && ("A_VENIR".equals(s.getDecision()) || "EN_ATTENTE".equals(s.getDecision())))
                    .forEach(s -> { s.setDecision("NON_REQUISE"); validationsRepo.save(s); });
            avenant.setStatut("REJETE".equals(decision) ? "REJETE" : "MODIFICATION_DEMANDEE");
            avenantsRepo.save(avenant);
            String title = "REJETE".equals(decision) ? "Avenant rejeté" : "Modifications demandées sur l'avenant";
            String msg = "L'avenant n°" + avenant.getNumero() + " du contrat " + contrat.getReference()
                    + ("REJETE".equals(decision) ? " a été rejeté." : " nécessite des corrections.");
            if (commentaire != null && !commentaire.isBlank()) msg += " Motif : " + commentaire.trim();
            notificationsService.notifyUserByEmail(avenant.getCreePar(), contrat.getId(), title, msg, "AVENANT");
            journauxAuditService.record(userId, contrat.getId(), contrat.getReference(),
                    "AVENANT_" + decision, "statut=EN_VALIDATION", "avenant=" + avenant.getId()
                            + "; statut=" + avenant.getStatut() + "; commentaire=" + commentaire);
            return saved;
        }

        if (!niveauApprouve) {
            notificationsService.notifyUserByEmail(avenant.getCreePar(), contrat.getId(),
                    "Approbation partielle de l'avenant", "Une approbation a été donnée pour l'avenant n°"
                            + avenant.getNumero() + ", mais une autre approbation du même niveau reste attendue.", "AVENANT");
            return saved;
        }

        Integer prochainNiveau = cycle.stream()
                .filter(s -> s.getNiveau() > v.getNiveau() && "A_VENIR".equals(s.getDecision()))
                .map(Validations::getNiveau).min(Integer::compareTo).orElse(null);
        if (prochainNiveau != null) {
            List<Validations> prochaines = cycle.stream()
                    .filter(s -> s.getNiveau().equals(prochainNiveau) && "A_VENIR".equals(s.getDecision())).toList();
            prochaines.forEach(s -> s.setDecision("EN_ATTENTE"));
            validationsRepo.saveAll(prochaines);
            prochaines.forEach(step -> notifierEtapeAvenant(step, avenant, contrat));
            notificationsService.notifyUserByEmail(avenant.getCreePar(), contrat.getId(),
                    "Étape suivante de l'avenant", "L'avenant n°" + avenant.getNumero()
                            + " passe à l'étape de validation suivante.", "AVENANT");
            return saved;
        }

        // Toutes les approbations sont obtenues : appliquer les changements et créer la version.
        String avant = "montant=" + contrat.getMontant() + "; dateFin=" + contrat.getDateFin();
        // Si aucune version n'existe encore, archiver l'état initial avant de créer la version modifiée.
        if (versionsRepo.findTopByContratIdOrderByVersionDesc(contrat.getId()).isEmpty()) {
            versionsRepo.save(VersionsContrat.builder().contratId(contrat.getId()).version(1)
                    .creePar(avenant.getCreePar()).contenu(snapshotContrat(contrat)).build());
        }
        if (avenant.getMontantApres() != null) contrat.setMontant(avenant.getMontantApres());
        if (avenant.getDateFinApres() != null) {
            if (!avenant.getDateFinApres().isAfter(contrat.getDateDebut()))
                throw new IllegalArgumentException("La date de fin de l'avenant doit être postérieure à la date de début.");
            contrat.setDateFin(avenant.getDateFinApres());
            contrat.setDureeMois((int) java.time.temporal.ChronoUnit.MONTHS.between(
                    contrat.getDateDebut(), avenant.getDateFinApres()));
        }
        contratRepo.save(contrat);
        avenant.setStatut("APPROUVE");
        avenantsRepo.save(avenant);

        int nextVersion = versionsRepo.findTopByContratIdOrderByVersionDesc(contrat.getId())
                .map(old -> old.getVersion() + 1).orElse(1);
        versionsRepo.save(VersionsContrat.builder().contratId(contrat.getId()).version(nextVersion)
                .avenantId(avenant.getId()).creePar(userId).contenu(snapshotContrat(contrat)).build());

        String apres = "montant=" + contrat.getMontant() + "; dateFin=" + contrat.getDateFin();
        journauxAuditService.record(userId, contrat.getId(), contrat.getReference(), "AVENANT_APPROUVE_APPLIQUE",
                avant, apres + "; avenant=" + avenant.getId() + "; version=" + nextVersion);
        String messageFinal = "L'avenant n°" + avenant.getNumero() + " du contrat " + contrat.getReference()
                + " a été approuvé. La version " + nextVersion + " du contrat est disponible.";
        notificationsService.notifyUserByEmail(avenant.getCreePar(), contrat.getId(),
                "Avenant entièrement approuvé", messageFinal, "AVENANT");
        if (!avenant.getCreePar().equals(contrat.getResponsableId()))
            notificationsService.notifyUserByEmail(contrat.getResponsableId(), contrat.getId(),
                    "Avenant approuvé", messageFinal, "AVENANT");
        return saved;
    }

    private void notifierEtapeAvenant(Validations step, Avenants avenant, Contrat contrat) {
        String title = "Avenant à approuver — " + contrat.getReference();
        String message = "L'avenant n°" + avenant.getNumero() + " attend votre décision (niveau "
                + step.getNiveau() + ").";
        if (step.getValideurId() != null) {
            notificationsService.notifyUserByEmail(step.getValideurId(), contrat.getId(), title, message, "AVENANT");
        } else {
            notificationsService.notifyRoleByEmail(Utilisateurs.Role.valueOf(step.getRoleRequis()),
                    contrat.getId(), title, message, "AVENANT");
        }
    }

    private java.util.Map<String, Object> snapshotContrat(Contrat c) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("reference", c.getReference()); data.put("objet", c.getObjet());
        data.put("description", c.getDescription()); data.put("clauses", c.getClauses());
        data.put("montant", c.getMontant()); data.put("devise", c.getDevise());
        data.put("dateSignature", c.getDateSignature()); data.put("dateDebut", c.getDateDebut());
        data.put("dateFin", c.getDateFin()); data.put("dureeMois", c.getDureeMois());
        data.put("service", c.getService()); data.put("renouvellementAutomatique", c.getRenouvellementAutomatique());
        data.put("joursPreavis", c.getJoursPreavis()); data.put("typeId", c.getTypeId());
        data.put("statut", c.getStatut());
        return data;
    }

    /**
     * Envoie une notification uniquement au rôle de cette étape (ou à l'utilisateur
     * explicitement affecté). Dans un circuit double, chaque étape possède son
     * rôle.
     */
    private void notifierEtape(Validations validation, String reference) {
        String title = "JURIDIQUE".equals(validation.getRoleRequis())
                ? "Contrat à approuver par le Juridique"
                : "Contrat à valider";
        String message = "Le contrat " + reference + " attend votre validation de niveau " + validation.getNiveau()
                + ".";
        if (validation.getValideurId() != null) {
            notificationsService.notifyUserByEmail(validation.getValideurId(), validation.getContratId(), title,
                message, "VALIDATION");
        } else {
            Utilisateurs.Role role = Utilisateurs.Role.valueOf(validation.getRoleRequis());
            notificationsService.notifyRoleByEmail(role, validation.getContratId(), title, message, "VALIDATION");
        }
    }

    /**
     * Retourne uniquement les étapes visibles par le compte connecté.
     * Un valideur et le service juridique ne doivent jamais consulter les
     * décisions ni les commentaires internes de l'autre rôle.
     */
    public List<Validations> all() {
        Authentication auth = currentAuth();

        // L'administrateur conserve une vue globale d'audit.
        if (hasRole(auth, "ADMIN"))
            return validationsRepo.findAll();

        Integer userId = currentUserId(auth);
        var rows = validationsRepo.findAll();

        // Le gestionnaire suit les validations de ses contrats et ses propres demandes.
        if (hasRole(auth, "GESTIONNAIRE")) {
            return rows.stream().filter(v -> userId.equals(v.getDemandeurId())
                    || (auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + v.getRoleRequis()))
                            && (userId.equals(v.getValideurId())
                                    || (v.getValideurId() == null && "EN_ATTENTE".equals(v.getDecision())))))
                    .toList();
        }

        // Les rôles de validation ne reçoivent que leurs étapes propres.
        // Les étapes attribuées à un utilisateur précis restent privées de ce compte.
        boolean isValideur = hasRole(auth, "VALIDEUR");
        boolean isJuridique = hasRole(auth, "JURIDIQUE");
        if (isValideur || isJuridique) {
            String roleVisible = isJuridique ? "JURIDIQUE" : "VALIDEUR";
            return rows.stream()
                    .filter(v -> roleVisible.equals(v.getRoleRequis()))
                    .filter(v -> userId.equals(v.getValideurId())
                            || (v.getValideurId() == null && "EN_ATTENTE".equals(v.getDecision())))
                    .toList();
        }

        // Par défaut, aucun autre rôle ne dispose d'une liste de validations.
        throw new AccessDeniedException("Accès interdit aux validations.");
    }

    public Validations create(Validations validation) {
        if (!hasRole(currentAuth(), "ADMIN"))
            throw new AccessDeniedException("Opération réservée à l'administrateur.");
        validation.setId(null);
        return validationsRepo.save(validation);
    }

    public Validations update(Long id, Validations details) {
        if (!hasRole(currentAuth(), "ADMIN"))
            throw new AccessDeniedException("Opération réservée à l'administrateur.");
        Validations validation = getOne(id);
        validation.setContratId(details.getContratId());
        validation.setValideurId(details.getValideurId());
        validation.setDemandeurId(details.getDemandeurId());
        validation.setNiveau(details.getNiveau());
        validation.setRoleRequis(details.getRoleRequis());
        validation.setCycle(details.getCycle());
        validation.setDecision(details.getDecision());
        validation.setCommentaire(details.getCommentaire());
        validation.setDateSoumission(details.getDateSoumission());
        validation.setDateValidation(details.getDateValidation());
        return validationsRepo.save(validation);
    }

    public void delete(Long id) {
        if (!hasRole(currentAuth(), "ADMIN"))
            throw new AccessDeniedException("Opération réservée à l'administrateur.");
        validationsRepo.delete(getOne(id));
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer))
            throw new AccessDeniedException("Authentification requise");
        return auth;
    }

    private Integer currentUserId(Authentication auth) {
        return (Integer) auth.getPrincipal();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
