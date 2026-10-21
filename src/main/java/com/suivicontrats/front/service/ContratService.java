package com.suivicontrats.front.service;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.entity.Validations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContratService {
    private final ContratRepository contratRepo;
    private final ValidationsRepository validationsRepo;

    public List<Contrat> getAll() {
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return contratRepo.findAll();
        if (hasRole(auth, "GESTIONNAIRE")) return contratRepo.findByResponsableId(currentUserId(auth));
        if (hasRole(auth, "VALIDEUR")) {
            var validations = new java.util.ArrayList<>(validationsRepo.findByValideurId(currentUserId(auth)));
            validations.addAll(validationsRepo.findByValideurIdIsNullAndRoleRequis("VALIDEUR"));
            var ids = validations.stream().map(Validations::getContratId).collect(java.util.stream.Collectors.toSet());
            return contratRepo.findAllById(ids);
        }
        throw new AccessDeniedException("Accès interdit");
    }
    public Contrat getById(Integer id) {
        Contrat contract = contratRepo.findById(id).orElseThrow(() -> new RuntimeException("Contrat non trouvé"));
        assertCanAccess(contract);
        return contract;
    }
    
    public Contrat create(Contrat contrat) {
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Accès interdit");
        contrat.setId(null);
        if (hasRole(auth, "GESTIONNAIRE")) {
            contrat.setResponsableId(currentUserId(auth));
            contrat.setCreePar(currentUserId(auth));
            if (!"BROUILLON".equals(contrat.getStatut()) && !"EN_ATTENTE".equals(contrat.getStatut())) {
                throw new AccessDeniedException("Un nouveau contrat doit rester au statut brouillon ou en attente.");
            }
        }
        if(contratRepo.existsByReference(contrat.getReference())){
            throw new RuntimeException("Référence déjà existante");
        }
        return contratRepo.save(contrat);
    }
    public Contrat update(Integer id, Contrat details) {
        Contrat c = getById(id);
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Accès interdit");
        if (hasRole(auth, "GESTIONNAIRE")
                && ((!"BROUILLON".equals(c.getStatut()) && !"EN_ATTENTE".equals(c.getStatut()))
                || (!"BROUILLON".equals(details.getStatut()) && !"EN_ATTENTE".equals(details.getStatut())))) {
            throw new AccessDeniedException("Le gestionnaire ne peut modifier que le contenu d'un brouillon ou d'un contrat en attente.");
        }
        if (details.getReference() == null || details.getReference().isBlank()) {
            throw new IllegalArgumentException("Le numéro de contrat est obligatoire");
        }
        if (!c.getReference().equals(details.getReference())
                && contratRepo.existsByReference(details.getReference())) {
            throw new RuntimeException("Référence déjà existante");
        }
        c.setReference(details.getReference());
        c.setObjet(details.getObjet());
        c.setDescription(details.getDescription());
        c.setClauses(details.getClauses());
        c.setMontant(details.getMontant());
        c.setDevise(details.getDevise());
        c.setDateSignature(details.getDateSignature());
        c.setStatut(details.getStatut());
        c.setDateDebut(details.getDateDebut());
        c.setDateFin(details.getDateFin());
        c.setDureeMois(details.getDureeMois());
        c.setService(details.getService());
        c.setRenouvellementAutomatique(details.getRenouvellementAutomatique());
        c.setJoursPreavis(details.getJoursPreavis());
        c.setResponsableId(hasRole(auth, "GESTIONNAIRE") ? currentUserId(auth) : details.getResponsableId());
        c.setTypeId(details.getTypeId());
        return contratRepo.save(c);
    }
    public void delete(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Accès interdit");
        if (hasRole(auth, "GESTIONNAIRE") && !"BROUILLON".equals(contract.getStatut())) {
            throw new AccessDeniedException("Seuls les brouillons peuvent être supprimés par un gestionnaire.");
        }
        contratRepo.delete(contract);
    }

    private void assertCanAccess(Contrat contract) {
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return;
        if (hasRole(auth, "GESTIONNAIRE") && java.util.Objects.equals(contract.getResponsableId(), currentUserId(auth))) return;
        if (hasRole(auth, "VALIDEUR")) {
            Integer userId = currentUserId(auth);
            boolean assigned = validationsRepo.findByContratId(contract.getId()).stream()
                    .anyMatch(v -> "VALIDEUR".equals(v.getRoleRequis())
                            && (java.util.Objects.equals(v.getValideurId(), userId) || v.getValideurId() == null));
            if (assigned) return;
        }
        throw new AccessDeniedException("Ce contrat ne relève pas de votre périmètre.");
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Integer)) throw new AccessDeniedException("Authentification requise");
        return auth;
    }
    private Integer currentUserId(Authentication auth) { return (Integer) auth.getPrincipal(); }
    private boolean hasRole(Authentication auth, String role) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)); }
}
