package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.repository.ReglesValidationRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.entity.Utilisateurs;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReglesValidationService {

    private final ReglesValidationRepository repo;
    private final UtilisateursRepository utilisateursRepo;

    public List<ReglesValidation> getAll(){ return repo.findAllByOrderByNiveauAsc(); }

    public ReglesValidation getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Règle de validation non trouvée"));
    }
    
    public List<ReglesValidation> getByType(Long typeId){ return repo.findByTypeContratIdOrderByNiveauAsc(typeId); }

    public ReglesValidation create(ReglesValidation r){
        Authentication auth = requireAdminOrJuridique();
        if (hasRole(auth, "ADMIN")) {
            // L'Admin choisit le type et le circuit; le seuil financier vient du Juridique.
            if (r.getRegleSourceId() == null)
                throw new IllegalArgumentException("Sélectionnez un seuil juridique auquel rattacher cette étape.");
            if (r.getTypeContratId() == null)
                throw new IllegalArgumentException("Sélectionnez un type de contrat pour cette règle.");
            ReglesValidation source = getOne(r.getRegleSourceId());
            r.setMontantMinimum(source.getMontantMinimum());
            r.setMontantMaximum(source.getMontantMaximum());
        } else {
            // Le Juridique définit les seuils financiers globaux; le type relève du circuit Admin.
            r.setTypeContratId(null);
            r.setNiveau(1);
            r.setRoleRequis("VALIDEUR");
            r.setValideurId(null);
        }
        validate(r);
        ensureUniqueLevel(r, null);
        r.setId(null);
        return repo.save(r);
    }

    public ReglesValidation update(Long id, ReglesValidation r){
        Authentication auth = requireAdminOrJuridique();
        ReglesValidation existing = getOne(id);
        if (hasRole(auth, "JURIDIQUE")) {
            validateThresholds(r);
            existing.setTypeContratId(r.getTypeContratId());
            existing.setMontantMinimum(r.getMontantMinimum());
            existing.setMontantMaximum(r.getMontantMaximum());
        } else {
            validateWorkflow(r);
            if (r.getTypeContratId() == null)
                throw new IllegalArgumentException("Sélectionnez un type de contrat pour cette règle.");
            r.setMontantMinimum(existing.getMontantMinimum());
            r.setMontantMaximum(existing.getMontantMaximum());
            ensureUniqueLevel(r, id);
            existing.setTypeContratId(r.getTypeContratId());
            existing.setNiveau(r.getNiveau());
            existing.setRoleRequis(r.getRoleRequis());
            existing.setValideurId(r.getValideurId());
        }
        return repo.save(existing);
    }

    private void validate(ReglesValidation r) {
        validateThresholds(r);
        validateWorkflow(r);
    }

    private void validateThresholds(ReglesValidation r) {
        if (r.getMontantMinimum() == null || r.getMontantMinimum().signum() < 0)
            throw new IllegalArgumentException("Le montant minimum doit être positif ou nul.");
        if (r.getMontantMaximum() != null && r.getMontantMaximum().compareTo(r.getMontantMinimum()) <= 0)
            throw new IllegalArgumentException("Le montant maximum doit être supérieur au minimum.");
    }

    private void validateWorkflow(ReglesValidation r) {
        if (r.getNiveau() == null || r.getNiveau() < 1) throw new IllegalArgumentException("Le niveau de validation doit être positif.");
        if (!List.of("VALIDEUR", "JURIDIQUE", "VALIDEUR_ET_JURIDIQUE").contains(r.getRoleRequis()))
            throw new IllegalArgumentException("Le rôle requis doit être VALIDEUR, JURIDIQUE ou les deux.");
        if ("VALIDEUR_ET_JURIDIQUE".equals(r.getRoleRequis()) && r.getValideurId() == null)
            throw new IllegalArgumentException("Choisissez le valideur chargé de cette étape conjointe.");
        if (r.getValideurId() != null) {
            Utilisateurs user = utilisateursRepo.findById(r.getValideurId())
                    .orElseThrow(() -> new IllegalArgumentException("L'utilisateur sélectionné n'existe pas."));
            String expectedRole = "VALIDEUR_ET_JURIDIQUE".equals(r.getRoleRequis()) ? "VALIDEUR" : r.getRoleRequis();
            if (!user.getRole().name().equals(expectedRole) || !Boolean.TRUE.equals(user.getActif()))
                throw new IllegalArgumentException("L'utilisateur ciblé doit être actif et avoir le rôle " + expectedRole + ".");
        }
    }

    private void ensureUniqueLevel(ReglesValidation candidate, Long excludedId) {
        boolean duplicate = repo.findAll().stream()
                .filter(existing -> excludedId == null || !excludedId.equals(existing.getId()))
                .anyMatch(existing -> java.util.Objects.equals(existing.getTypeContratId(), candidate.getTypeContratId())
                        && sameAmount(existing.getMontantMinimum(), candidate.getMontantMinimum())
                        && sameAmount(existing.getMontantMaximum(), candidate.getMontantMaximum())
                        && java.util.Objects.equals(existing.getNiveau(), candidate.getNiveau())
                        && overlapsRoles(existing.getRoleRequis(), candidate.getRoleRequis()));
        if (duplicate)
            throw new IllegalArgumentException("Ce seuil possède déjà une étape pour ce rôle à ce niveau.");
    }

    private boolean overlapsRoles(String first, String second) {
        return rolesIn(first).stream().anyMatch(rolesIn(second)::contains);
    }

    private java.util.Set<String> rolesIn(String role) {
        return "VALIDEUR_ET_JURIDIQUE".equals(role)
                ? java.util.Set.of("VALIDEUR", "JURIDIQUE")
                : java.util.Set.of(role);
    }

    private boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    public void delete(Long id){
        Authentication auth = requireAdminOrJuridique();
        if (!hasRole(auth, "ADMIN")) {
            throw new AccessDeniedException("La suppression d'une étape de circuit est réservée à l'administrateur.");
        }
        repo.deleteById(id);
    }

    private Authentication requireAdminOrJuridique() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || (!hasRole(auth, "ADMIN") && !hasRole(auth, "JURIDIQUE"))) {
            throw new AccessDeniedException("Configuration réservée à l'administrateur ou au responsable juridique.");
        }
        return auth;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    // C'est ce que tu vas appeler pour un contrat
    public List<ReglesValidation> getCircuit(Long typeContratId, BigDecimal montant){
        java.util.Map<Integer, List<ReglesValidation>> byLevel = new java.util.HashMap<>();
        for (ReglesValidation rule : repo.findCircuitApplicable(typeContratId, montant)) {
            byLevel.computeIfAbsent(rule.getNiveau(), ignored -> new java.util.ArrayList<>()).add(rule);
        }
        return byLevel.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(entry -> {
                    List<ReglesValidation> rules = entry.getValue();
                    java.util.Set<String> typeSpecificRoles = rules.stream()
                            .filter(r -> typeContratId != null && typeContratId.equals(r.getTypeContratId()))
                            .flatMap(r -> rolesIn(r.getRoleRequis()).stream())
                            .collect(java.util.stream.Collectors.toSet());
                    // Une règle propre au type remplace le rôle correspondant de la règle globale,
                    // sans effacer une approbation globale distincte au même niveau.
                    return rules.stream().filter(r -> {
                        if (typeContratId != null && typeContratId.equals(r.getTypeContratId())) return true;
                        return rolesIn(r.getRoleRequis()).stream().noneMatch(typeSpecificRoles::contains);
                    });
                }).toList();
    }
}
