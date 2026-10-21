package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.ContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    public List<Validations> genererCircuit(Integer contratId, Integer demandeurId){
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Opération réservée au gestionnaire.");
        if (!hasRole(auth, "ADMIN")) {
            var contract = contratRepo.findById(contratId).orElseThrow(() -> new RuntimeException("Contrat non trouvé"));
            if (!currentUserId(auth).equals(contract.getResponsableId())) throw new AccessDeniedException("Ce contrat ne relève pas de votre périmètre.");
            if (!"BROUILLON".equals(contract.getStatut()) && !"EN_ATTENTE".equals(contract.getStatut())) throw new IllegalArgumentException("Seul un brouillon peut être soumis en validation.");
        }
        if (validationsRepo.findByContratId(contratId).stream().anyMatch(v -> "EN_ATTENTE".equals(v.getDecision()))) throw new IllegalArgumentException("Un circuit de validation est déjà en cours.");
        demandeurId = currentUserId(auth);
        Integer cycle = validationsRepo.findTopByContratIdOrderByCycleDesc(contratId)
                .map(previous -> previous.getCycle() + 1)
                .orElse(1);
        Validations v = Validations.builder()
                .contratId(contratId)
                .demandeurId(demandeurId)
                .niveau(1)
                .roleRequis("VALIDEUR")
                .cycle(cycle)
                .decision("EN_ATTENTE")
                .dateSoumission(LocalDateTime.now())
                .build();
        List<Validations> saved = validationsRepo.saveAll(List.of(v));
        contratRepo.findById(contratId).ifPresent(contract -> { contract.setStatut("EN_VALIDATION"); contratRepo.save(contract); });
        return saved;
    }

    public Validations decider(Long validationId, Integer valideurId, String decision, String commentaire){
        Authentication auth = currentAuth();
        if (!hasRole(auth, "VALIDEUR") || !valideurId.equals(currentUserId(auth))) throw new AccessDeniedException("Décision réservée au valideur connecté.");
        if (!List.of("APPROUVE", "REJETE", "MODIFICATION_DEMANDEE").contains(decision)) {
            throw new IllegalArgumentException("Décision de validation invalide");
        }
        Validations v = getOne(validationId);
        if (!"VALIDEUR".equals(v.getRoleRequis()) || (v.getValideurId() != null && !v.getValideurId().equals(valideurId))) {
            throw new AccessDeniedException("Cette validation ne vous est pas attribuée.");
        }
        v.setValideurId(valideurId);
        v.setDecision(decision);
        v.setCommentaire(commentaire);
        v.setDateValidation(LocalDateTime.now());
        Validations saved = validationsRepo.save(v);
        contratRepo.findById(v.getContratId()).ifPresent(contract -> {
            if ("APPROUVE".equals(decision)) contract.setStatut("APPROUVE");
            else if ("REJETE".equals(decision)) contract.setStatut("REJETE");
            else contract.setStatut("BROUILLON");
            contratRepo.save(contract);
        });
        return saved;
    }

    public List<Validations> all(){
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return validationsRepo.findAll();
        Integer userId = currentUserId(auth);
        if (hasRole(auth, "GESTIONNAIRE")) return validationsRepo.findAll().stream().filter(v -> userId.equals(v.getDemandeurId())).toList();
        if (hasRole(auth, "VALIDEUR")) return validationsRepo.findAll().stream()
                .filter(v -> "VALIDEUR".equals(v.getRoleRequis()) && (v.getValideurId() == null || userId.equals(v.getValideurId())))
                .toList();
        throw new AccessDeniedException("Accès interdit");
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
