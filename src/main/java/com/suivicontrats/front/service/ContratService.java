package com.suivicontrats.front.service;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.DocumentsContratRepository;
import org.springframework.transaction.annotation.Transactional;
import com.suivicontrats.front.entity.Validations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ContratService {
    private final ContratRepository contratRepo;
    private final ValidationsRepository validationsRepo;
    private final DocumentsContratRepository documentsRepo;
    private final JournauxAuditService journauxAuditService;

    public List<Contrat> getAll() {
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return contratRepo.findAll();
        if (hasRole(auth, "GESTIONNAIRE")) return contratRepo.findByResponsableId(currentUserId(auth));
        var validatorRoles = auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring("ROLE_".length()))
                .filter("VALIDEUR"::equals).toList();
        if (!validatorRoles.isEmpty()) {
            var validations = new java.util.ArrayList<>(validationsRepo.findByValideurId(currentUserId(auth)));
            validatorRoles.forEach(role -> validations.addAll(validationsRepo.findByValideurIdIsNullAndRoleRequis(role).stream()
                    .filter(v -> "EN_ATTENTE".equals(v.getDecision())).toList()));
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
    
    @Transactional
    public Contrat create(Contrat contrat) {
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Accès interdit");
        contrat.setId(null);
        // Le responsable et le créateur sont toujours l'utilisateur authentifié.
        contrat.setResponsableId(currentUserId(auth));
        contrat.setCreePar(currentUserId(auth));
        if (!"BROUILLON".equals(contrat.getStatut()))
            throw new IllegalArgumentException("Un nouveau contrat doit commencer au statut brouillon.");
        if (hasRole(auth, "GESTIONNAIRE")) {
            if (!"BROUILLON".equals(contrat.getStatut()) && !"EN_ATTENTE".equals(contrat.getStatut())) {
                throw new AccessDeniedException("Un nouveau contrat doit rester au statut brouillon ou en attente.");
            }
        }
        if(contratRepo.existsByReference(contrat.getReference())){
            throw new RuntimeException("Référence déjà existante");
        }
        Contrat saved = contratRepo.save(contrat);
        journauxAuditService.record(currentUserId(auth), saved.getId(), saved.getReference(),
                "CONTRAT_CREE", null, auditSummary(saved));
        return saved;
    }
    @Transactional
    public Contrat update(Integer id, Contrat details) {
        Contrat c = getById(id);
        String oldValue = auditSummary(c);
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
        if (!java.util.Objects.equals(c.getStatut(), details.getStatut()))
            throw new AccessDeniedException("Le changement de statut doit passer par l’action métier correspondante.");
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
        Contrat saved = contratRepo.save(c);
        journauxAuditService.record(currentUserId(auth), saved.getId(), saved.getReference(),
                "CONTRAT_MODIFIE", oldValue, auditSummary(saved));
        return saved;
    }
    @Transactional
    public void delete(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Accès interdit");
        if (hasRole(auth, "GESTIONNAIRE") && !"BROUILLON".equals(contract.getStatut())) {
            throw new AccessDeniedException("Seuls les brouillons peuvent être supprimés par un gestionnaire.");
        }
        journauxAuditService.record(currentUserId(auth), contract.getId(), contract.getReference(), "CONTRAT_SUPPRIME", auditSummary(contract), null);
        contratRepo.delete(contract);
    }

    @Transactional
    public Contrat sign(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Signature réservée au gestionnaire ou à l'administrateur.");
        if (!"APPROUVE".equals(contract.getStatut())) throw new IllegalArgumentException("Seul un contrat entièrement approuvé peut être marqué signé.");
        var signedPdf = documentsRepo.findByContratIdOrderByDateImportDesc(contract.getId()).stream()
                .filter(document -> "CONTRAT_SIGNE".equals(document.getCategorie()))
                .filter(document -> java.nio.file.Files.isRegularFile(java.nio.file.Paths.get(document.getChemin())))
                .findFirst();
        if (signedPdf.isEmpty())
            throw new IllegalArgumentException("Déposez d'abord le PDF du contrat signé dans les documents du contrat.");
        String old = auditSummary(contract);
        contract.setStatut("SIGNE");
        contract.setDateSignature(LocalDate.now());
        Contrat saved = contratRepo.save(contract);
        journauxAuditService.record(currentUserId(auth), saved.getId(), saved.getReference(), "CONTRAT_SIGNE", old, auditSummary(saved));
        return saved;
    }

    @Transactional
    public Contrat terminate(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) throw new AccessDeniedException("Résiliation réservée au gestionnaire ou à l'administrateur.");
        if (!List.of("SIGNE", "ACTIF").contains(contract.getStatut())) throw new IllegalArgumentException("Seul un contrat signé ou actif peut être résilié.");
        String old = auditSummary(contract);
        contract.setStatut("RESILIE");
        Contrat saved = contratRepo.save(contract);
        journauxAuditService.record(currentUserId(auth), saved.getId(), saved.getReference(), "CONTRAT_RESILIE", old, auditSummary(saved));
        return saved;
    }

    private void assertCanAccess(Contrat contract) {
        Authentication auth = currentAuth();
        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) return;
        if (hasRole(auth, "GESTIONNAIRE") && java.util.Objects.equals(contract.getResponsableId(), currentUserId(auth))) return;
        if (hasRole(auth, "VALIDEUR")) {
            Integer userId = currentUserId(auth);
            List<String> validatorRoles = auth.getAuthorities().stream().map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring("ROLE_".length()))
                    .filter("VALIDEUR"::equals).toList();
            boolean assigned = validationsRepo.findByContratId(contract.getId()).stream()
                    .anyMatch(v -> validatorRoles.contains(v.getRoleRequis())
                            && (java.util.Objects.equals(v.getValideurId(), userId)
                            || (v.getValideurId() == null && "EN_ATTENTE".equals(v.getDecision()))));
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
    private String auditSummary(Contrat contract) {
        return "référence=" + contract.getReference()
                + "; statut=" + contract.getStatut()
                + "; objet=" + contract.getObjet()
                + "; responsableId=" + contract.getResponsableId()
                + "; montant=" + contract.getMontant();
    }
}
