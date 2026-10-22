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
            // L'administrateur configure l'étape et son rôle, pas le seuil métier.
            r.setTypeContratId(null);
            r.setMontantMinimum(BigDecimal.ZERO);
            r.setMontantMaximum(null);
        } else {
            // Le juridique définit les seuils; l'étape est configurée par l'administrateur.
            r.setNiveau(1);
            r.setRoleRequis("VALIDEUR");
            r.setValideurId(null);
        }
        validate(r);
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
        if (!"VALIDEUR".equals(r.getRoleRequis()))
            throw new IllegalArgumentException("Seul le rôle VALIDEUR peut être configuré dans un circuit.");
        if (r.getValideurId() != null) {
            Utilisateurs user = utilisateursRepo.findById(r.getValideurId())
                    .orElseThrow(() -> new IllegalArgumentException("Le valideur sélectionné n'existe pas."));
            if (user.getRole() != Utilisateurs.Role.VALIDEUR || !Boolean.TRUE.equals(user.getActif()))
                throw new IllegalArgumentException("Le compte choisi doit être un valideur actif.");
        }
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
        java.util.Map<Integer, ReglesValidation> byLevel = new java.util.HashMap<>();
        for (ReglesValidation rule : repo.findCircuitApplicable(typeContratId, montant)) {
            ReglesValidation current = byLevel.get(rule.getNiveau());
            if (current == null || (rule.getTypeContratId() != null && rule.getTypeContratId().equals(typeContratId)))
                byLevel.put(rule.getNiveau(), rule);
        }
        return byLevel.values().stream().sorted(java.util.Comparator.comparing(ReglesValidation::getNiveau)).toList();
    }
}
