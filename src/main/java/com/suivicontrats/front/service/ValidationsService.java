package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
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

    public List<Validations> getByContrat(Integer contratId){
        return all().stream().filter(v -> v.getContratId().equals(contratId)).toList();
    }

    public Validations getOne(Long id) {
        Validations validation = validationsRepo.findById(id).orElseThrow(() -> new RuntimeException("Validation non trouvée"));
        if (all().stream().noneMatch(v -> v.getId().equals(id))) throw new AccessDeniedException("Cette validation ne relève pas de votre périmètre.");
        return validation;
    }

    public List<Validations> getEnAttente(){ 
        return validationsRepo.findByDecision("EN_ATTENTE"); 
    }

    @Transactional
    public List<Validations> genererCircuit(Integer contratId, Integer demandeurId){
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Opération réservée au gestionnaire.");
        Contrat contract = contratRepo.findById(contratId).orElseThrow(() -> new RuntimeException("Contrat non trouvé"));
        if (!hasRole(auth, "ADMIN") && !currentUserId(auth).equals(contract.getResponsableId())) throw new AccessDeniedException("Ce contrat ne relève pas de votre périmètre.");
        if (!"BROUILLON".equals(contract.getStatut()) && !"EN_ATTENTE".equals(contract.getStatut())) throw new IllegalArgumentException("Seul un brouillon peut être soumis en validation.");
        if (validationsRepo.findByContratId(contratId).stream().anyMatch(v -> "EN_ATTENTE".equals(v.getDecision()))) throw new IllegalArgumentException("Un circuit de validation est déjà en cours.");
        demandeurId = currentUserId(auth);
        Integer cycle = validationsRepo.findTopByContratIdOrderByCycleDesc(contratId)
                .map(previous -> previous.getCycle() + 1)
                .orElse(1);
        List<com.suivicontrats.front.entity.ReglesValidation> circuit = reglesValidationService.getCircuit(contract.getTypeId(), contract.getMontant());
        if (circuit.isEmpty()) throw new IllegalArgumentException("Aucune règle de validation ne couvre le type et le montant de ce contrat.");
        List<Validations> steps = new java.util.ArrayList<>();
        for (var rule : circuit) {
            if (!"VALIDEUR".equals(rule.getRoleRequis())) {
                throw new IllegalArgumentException("Chaque étape du circuit doit être attribuée au rôle VALIDEUR.");
            }
            steps.add(Validations.builder().contratId(contratId).demandeurId(demandeurId)
                    .niveau(rule.getNiveau()).roleRequis(rule.getRoleRequis()).valideurId(rule.getValideurId()).cycle(cycle)
                    .decision(steps.isEmpty() ? "EN_ATTENTE" : "A_VENIR")
                    .dateSoumission(LocalDateTime.now()).build());
        }
        List<Validations> saved = validationsRepo.saveAll(steps);
        String ancienStatut = contract.getStatut();
        contract.setStatut("EN_VALIDATION");
        contratRepo.save(contract);

        String reference = contract.getReference() == null ? "#" + contratId : contract.getReference();
        notifierEtape(saved.get(0), reference);
        journauxAuditService.record(
                demandeurId, contratId, reference, "CONTRAT_SOUMIS_VALIDATION",
                "statut=" + ancienStatut, "statut=EN_VALIDATION; cycle=" + cycle);
        return saved;
    }

    @Transactional
    public Validations decider(Long validationId, Integer valideurId, String decision, String commentaire){
        Authentication auth = currentAuth();
        if (!valideurId.equals(currentUserId(auth))) throw new AccessDeniedException("Décision réservée à l'utilisateur connecté.");
        if (!List.of("APPROUVE", "REJETE", "MODIFICATION_DEMANDEE").contains(decision)) {
            throw new IllegalArgumentException("Décision de validation invalide");
        }
        if (!hasRole(auth, "VALIDEUR")) throw new AccessDeniedException("Seul un valideur peut décider sur un contrat.");
        Validations v = getOne(validationId);
        String requiredRole = "ROLE_" + v.getRoleRequis();
        if (!"ROLE_VALIDEUR".equals(requiredRole)
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
        List<Validations> cycleSteps = validationsRepo.findByContratIdAndCycleOrderByNiveauAsc(v.getContratId(), v.getCycle());
        Validations nextStep = cycleSteps.stream().filter(s -> s.getNiveau() > v.getNiveau() && "A_VENIR".equals(s.getDecision())).findFirst().orElse(null);
        if ("APPROUVE".equals(decision) && nextStep != null) {
            nextStep.setDecision("EN_ATTENTE");
            validationsRepo.save(nextStep);
            contratRepo.findById(v.getContratId()).ifPresent(c -> notificationsService.notifyUser(c.getResponsableId(), c.getId(), "Étape de validation approuvée", "Le niveau " + v.getNiveau() + " du contrat " + c.getReference() + " est approuvé. Le dossier passe au niveau " + nextStep.getNiveau() + ".", "VALIDATION"));
            notifierEtape(nextStep, contratRepo.findById(v.getContratId()).map(Contrat::getReference).orElse("#" + v.getContratId()));
        } else if (!"APPROUVE".equals(decision)) {
            cycleSteps.stream().filter(s -> "A_VENIR".equals(s.getDecision())).forEach(s -> { s.setDecision("NON_REQUISE"); validationsRepo.save(s); });
        }
        contratRepo.findById(v.getContratId()).ifPresent(contract -> {
            String ancienStatut = contract.getStatut();
            if ("APPROUVE".equals(decision) && nextStep == null) contract.setStatut("APPROUVE");
            else if ("APPROUVE".equals(decision)) contract.setStatut("EN_VALIDATION");
            else if ("REJETE".equals(decision)) contract.setStatut("REJETE");
            else contract.setStatut("BROUILLON");
            contratRepo.save(contract);

            String action = switch (decision) {
                case "APPROUVE" -> "CONTRAT_APPROUVE";
                case "REJETE" -> "CONTRAT_REJETE";
                default -> "MODIFICATION_DEMANDEE";
            };
            String nouvelleValeur = "statut=" + contract.getStatut() + "; decision=" + decision;
            if (commentaire != null && !commentaire.isBlank()) nouvelleValeur += "; commentaire=" + commentaire.trim();
            journauxAuditService.record(
                    valideurId, contract.getId(), contract.getReference(), action,
                    "statut=" + ancienStatut + "; decision=EN_ATTENTE", nouvelleValeur);

            Integer responsableId = contract.getResponsableId();
            if ("MODIFICATION_DEMANDEE".equals(decision)) {
                String message = "Le valideur a demandé des modifications pour le contrat " + contract.getReference() + ".";
                if (commentaire != null && !commentaire.isBlank()) message += " Commentaire : " + commentaire.trim();
                notificationsService.notifyUser(responsableId, contract.getId(), "Modifications demandées", message, "VALIDATION");
            } else if ("APPROUVE".equals(decision) && nextStep == null) {
                notificationsService.notifyUser(responsableId, contract.getId(), "Contrat approuvé", "Le contrat " + contract.getReference() + " a été approuvé.", "VALIDATION");
            } else if ("REJETE".equals(decision)) {
                String message = "Le contrat " + contract.getReference() + " a été rejeté.";
                if (commentaire != null && !commentaire.isBlank()) message += " Commentaire : " + commentaire.trim();
                notificationsService.notifyUser(responsableId, contract.getId(), "Contrat rejeté", message, "VALIDATION");
            }
        });
        return saved;
    }

    private void notifierEtape(Validations validation, String reference) {
        String title = "Contrat à valider";
        String message = "Le contrat " + reference + " attend une validation de niveau " + validation.getNiveau() + ".";
        if (validation.getValideurId() != null) {
            notificationsService.notifyUser(validation.getValideurId(), validation.getContratId(), title, message, "VALIDATION");
        } else {
            notificationsService.notifyUsers(utilisateursRepo.findByRoleAndActifTrue(Utilisateurs.Role.VALIDEUR), validation.getContratId(), title, message, "VALIDATION");
        }
    }

    public List<Validations> all(){
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return validationsRepo.findAll();
        Integer userId = currentUserId(auth);
        var rows = validationsRepo.findAll();
        if (hasRole(auth, "GESTIONNAIRE")) return rows.stream().filter(v -> userId.equals(v.getDemandeurId())
                || (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + v.getRoleRequis()))
                && (userId.equals(v.getValideurId())
                || (v.getValideurId() == null && "EN_ATTENTE".equals(v.getDecision()))))).toList();
        return rows.stream().filter(v -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + v.getRoleRequis()))
                && (userId.equals(v.getValideurId())
                || (v.getValideurId() == null && "EN_ATTENTE".equals(v.getDecision())))).toList();
    }

    public Validations create(Validations validation) {
        if (!hasRole(currentAuth(), "ADMIN")) throw new AccessDeniedException("Opération réservée à l'administrateur.");
        validation.setId(null);
        return validationsRepo.save(validation);
    }

    public Validations update(Long id, Validations details) {
        if (!hasRole(currentAuth(), "ADMIN")) throw new AccessDeniedException("Opération réservée à l'administrateur.");
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
        if (!hasRole(currentAuth(), "ADMIN")) throw new AccessDeniedException("Opération réservée à l'administrateur.");
        validationsRepo.delete(getOne(id));
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer)) throw new AccessDeniedException("Authentification requise");
        return auth;
    }
    private Integer currentUserId(Authentication auth) { return (Integer) auth.getPrincipal(); }
    private boolean hasRole(Authentication auth, String role) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)); }
}
