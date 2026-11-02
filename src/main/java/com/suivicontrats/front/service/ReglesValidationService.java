package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.repository.ReglesValidationRepository;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.entity.Utilisateurs;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReglesValidationService {

    private final ReglesValidationRepository repo;
    private final ContratRepository contratRepo;
    private final UtilisateursRepository utilisateursRepo;
    private final NotificationsService notificationsService;

    public List<ReglesValidation> getAll() {
        return repo.findAllByOrderByNiveauAsc();
    }

    public ReglesValidation getOne(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Règle de validation non trouvée"));
    }

    public List<ReglesValidation> getByType(Long typeId) {
        return repo.findByTypeContratIdOrderByNiveauAsc(typeId);
    }

    @Transactional
    public ReglesValidation create(ReglesValidation r) {
        requireAdmin();
        if (r.getTypeContratId() == null)
            throw new IllegalArgumentException("Sélectionnez un type de contrat pour cette règle.");
        validateWorkflow(r);
        r.setId(null);
        r.setMontantMinimum(BigDecimal.ZERO);
        r.setMontantMaximum(null);
        r.setStatut("EN_ATTENTE_SEUIL");
        ReglesValidation saved = repo.save(r);
        notificationsService.notifyRuleRole(Utilisateurs.Role.JURIDIQUE,
                saved.getId(), "Circuit de validation à compléter",
                "L’ADMIN a créé une règle de niveau " + saved.getNiveau() + ". Définissez son seuil financier.");
        return saved;
    }

    @Transactional
    public ReglesValidation update(Long id, ReglesValidation r) {
        Authentication auth = requireAdminOrJuridique();
        ReglesValidation existing = getOne(id);
        if (hasRole(auth, "JURIDIQUE")) {
            validateThresholds(r);
            existing.setMontantMinimum(r.getMontantMinimum());
            existing.setMontantMaximum(r.getMontantMaximum());
            existing.setStatut("EN_ATTENTE_CONFIRMATION");
            ReglesValidation saved = repo.save(existing);
            notificationsService.notifyRuleRole(Utilisateurs.Role.ADMIN,
                    saved.getId(), "Seuil de validation à confirmer",
                    "Le Juridique a défini le seuil de la règle de niveau " + saved.getNiveau()
                            + ". Confirmez son activation.");
            return saved;
        } else {
            validateWorkflow(r);
            if (r.getTypeContratId() == null)
                throw new IllegalArgumentException("Sélectionnez un type de contrat pour cette règle.");
            existing.setTypeContratId(r.getTypeContratId());
            existing.setNiveau(r.getNiveau());
            existing.setRoleRequis(r.getRoleRequis());
            existing.setValideurId(r.getValideurId());
            existing.setMontantMinimum(BigDecimal.ZERO);
            existing.setMontantMaximum(null);
            existing.setStatut("EN_ATTENTE_SEUIL");
            ReglesValidation saved = repo.save(existing);
            notificationsService.notifyRuleRole(Utilisateurs.Role.JURIDIQUE,
                    saved.getId(), "Circuit de validation modifié",
                    "L’ADMIN a modifié une règle de niveau " + saved.getNiveau()
                            + ". Vérifiez et redéfinissez son seuil.");
            return saved;
        }
    }

    @Transactional
    public ReglesValidation confirm(Long id) {
        requireAdmin();
        ReglesValidation rule = getOne(id);
        if (!"EN_ATTENTE_CONFIRMATION".equals(rule.getStatut()))
            throw new IllegalStateException("Seule une règle dont le seuil est défini peut être confirmée.");
        validateThresholds(rule);
        ensureUniqueLevel(rule, id);
        List<Contrat> newlyCoveredContracts = contratRepo
            .findByStatutIn(List.of("BROUILLON", "EN_ATTENTE", "REJETE")).stream()
            .filter(contract -> contract.getMontant() != null)
            .filter(contract -> rule.getTypeContratId() == null
                || java.util.Objects.equals(contract.getTypeId(), rule.getTypeContratId()))
            .filter(contract -> rule.getMontantMinimum() != null
                && rule.getMontantMinimum().compareTo(contract.getMontant()) <= 0)
            .filter(contract -> rule.getMontantMaximum() == null
                || rule.getMontantMaximum().compareTo(contract.getMontant()) > 0)
                .toList();
        rule.setStatut("ACTIVE");
        ReglesValidation saved = repo.save(rule);
        newlyCoveredContracts.stream()
                .forEach(contract -> notificationsService.notifyUser(contract.getResponsableId(), contract.getId(),
                "Règle de validation désormais active",
                "La règle de validation " + saved.getId() + " couvre désormais le type de contrat #"
                    + contract.getTypeId() + " et le montant " + contract.getMontant() + " "
                    + contract.getDevise() + " du contrat " + contract.getReference()
                    + ". Vous pouvez le soumettre au circuit de validation.",
                        "INFO"));
        notificationsService.notifyRuleRole(Utilisateurs.Role.JURIDIQUE,
                saved.getId(), "Règle de validation activée",
                "L’ADMIN a confirmé et activé la règle de niveau " + saved.getNiveau() + ".");
        return saved;
    }

    private void validateThresholds(ReglesValidation r) {
        if (r.getMontantMinimum() == null || r.getMontantMinimum().signum() < 0)
            throw new IllegalArgumentException("Le montant minimum doit être positif ou nul.");
        if (r.getMontantMaximum() != null && r.getMontantMaximum().compareTo(r.getMontantMinimum()) <= 0)
            throw new IllegalArgumentException("Le montant maximum doit être supérieur au minimum.");
    }

    private void validateWorkflow(ReglesValidation r) {
        if (r.getNiveau() == null || r.getNiveau() < 1)
            throw new IllegalArgumentException("Le niveau de validation doit être positif.");
        if (!List.of("VALIDEUR", "JURIDIQUE", "VALIDEUR_ET_JURIDIQUE").contains(r.getRoleRequis()))
            throw new IllegalArgumentException("Le rôle requis doit être VALIDEUR, JURIDIQUE ou les deux.");
        if ("VALIDEUR_ET_JURIDIQUE".equals(r.getRoleRequis()) && r.getValideurId() == null)
            throw new IllegalArgumentException("Choisissez le valideur chargé de cette étape conjointe.");
        if (r.getValideurId() != null) {
            Utilisateurs user = utilisateursRepo.findById(r.getValideurId())
                    .orElseThrow(() -> new IllegalArgumentException("L'utilisateur sélectionné n'existe pas."));
            String expectedRole = "VALIDEUR_ET_JURIDIQUE".equals(r.getRoleRequis()) ? "VALIDEUR" : r.getRoleRequis();
            if (!user.getRole().name().equals(expectedRole) || !Boolean.TRUE.equals(user.getActif()))
                throw new IllegalArgumentException(
                        "L'utilisateur ciblé doit être actif et avoir le rôle " + expectedRole + ".");
        }
    }

    private void ensureUniqueLevel(ReglesValidation candidate, Long excludedId) {
        boolean duplicate = repo.findAll().stream()
                .filter(existing -> excludedId == null || !excludedId.equals(existing.getId()))
                .filter(existing -> "ACTIVE".equals(existing.getStatut())
                        || "EN_ATTENTE_CONFIRMATION".equals(existing.getStatut()))
                .anyMatch(
                        existing -> java.util.Objects.equals(existing.getTypeContratId(), candidate.getTypeContratId())
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

    public void delete(Long id) {
        Authentication auth = requireAdminOrJuridique();
        if (!hasRole(auth, "ADMIN")) {
            throw new AccessDeniedException("La suppression d'une étape de circuit est réservée à l'administrateur.");
        }
        repo.deleteById(id);
    }

    private Authentication requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !hasRole(auth, "ADMIN"))
            throw new AccessDeniedException(
                    "La création et la confirmation des règles sont réservées à l’administrateur.");
        return auth;
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
    public List<ReglesValidation> getCircuit(Long typeContratId, BigDecimal montant) {
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
                        if (typeContratId != null && typeContratId.equals(r.getTypeContratId()))
                            return true;
                        return rolesIn(r.getRoleRequis()).stream().noneMatch(typeSpecificRoles::contains);
                    });
                }).toList();
    }
}
