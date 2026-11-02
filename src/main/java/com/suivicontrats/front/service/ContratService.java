
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ObligationsContratRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.DocumentsContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ContratService {

    private final ContratRepository contratRepo;
    private final ValidationsRepository validationsRepo;
    private final DocumentsContratRepository documentsRepo;
    private final JournauxAuditService journauxAuditService;
    private final ReglesValidationService reglesValidationService;
    private final NotificationsService notificationsService;
    private final UtilisateursRepository utilisateursRepo;
    private final ObligationsContratRepository obligationsContratRepository;
    private final VersionsContratRepository versionsRepo;

    public List<Contrat> getAll() {
        Authentication auth = currentAuth();

        if (hasRole(auth, "ADMIN")) {
            return contratRepo.findAll();
        }

        if (hasRole(auth, "JURIDIQUE")) {
            var idsEnCircuit = validationsRepo.findAll().stream()
                    .map(Validations::getContratId)
                    .collect(java.util.stream.Collectors.toSet());

            return contratRepo.findAllById(idsEnCircuit);
        }

        if (hasRole(auth, "GESTIONNAIRE")) {
            return contratRepo.findByResponsableId(currentUserId(auth));
        }

        var validatorRoles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .filter("VALIDEUR"::equals)
                .toList();

        if (!validatorRoles.isEmpty()) {
            var validations = new java.util.ArrayList<>(
                    validationsRepo.findByValideurId(currentUserId(auth)));

            validatorRoles.forEach(role ->
                    validations.addAll(
                            validationsRepo.findByValideurIdIsNullAndRoleRequis(role)
                                    .stream()
                                    .filter(v -> "EN_ATTENTE".equals(v.getDecision()))
                                    .toList()
                    )
            );

            var ids = validations.stream()
                    .map(Validations::getContratId)
                    .collect(java.util.stream.Collectors.toSet());

            return contratRepo.findAllById(ids);
        }

        throw new AccessDeniedException("Accès interdit");
    }

    public List<Contrat> getPendingLegalApproval() {
        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "JURIDIQUE")) {
            throw new AccessDeniedException(
                    "Consultation réservée au juridique ou à l'administrateur."
            );
        }

        var contractIds = validationsRepo.findByDecision("EN_ATTENTE")
                .stream()
                .filter(validation -> "JURIDIQUE".equals(validation.getRoleRequis()))
                .map(Validations::getContratId)
                .collect(java.util.stream.Collectors.toSet());

        return contratRepo.findAllById(contractIds);
    }

    public Contrat getById(Integer id) {
        Contrat contract = contratRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Contrat non trouvé"));

        assertCanAccess(contract);
        return contract;
    }

    /**
     * Création d'un contrat avec génération automatique
     * de sa première version.
     */
    @Transactional
    public Contrat create(Contrat contrat) {
        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) {
            throw new AccessDeniedException("Accès interdit");
        }

        contrat.setId(null);
        contrat.setResponsableId(currentUserId(auth));
        contrat.setCreePar(currentUserId(auth));
        contrat.setService(serviceResponsable(contrat.getResponsableId()));

        if (!"BROUILLON".equals(contrat.getStatut())) {
            throw new IllegalArgumentException(
                    "Un nouveau contrat doit commencer au statut brouillon."
            );
        }

        if (contratRepo.existsByReference(contrat.getReference())) {
            throw new RuntimeException("Référence déjà existante");
        }

        Contrat saved = contratRepo.save(contrat);

        // Première version automatique : version 1.
        creerVersion(saved, currentUserId(auth), null);

        journauxAuditService.record(
                currentUserId(auth),
                saved.getId(),
                saved.getReference(),
                "CONTRAT_CREE",
                null,
                auditSummary(saved)
        );

        notifyIfNoValidationRule(saved);

        return saved;
    }

    /**
     * Modification d'un contrat.
     * Une nouvelle version est créée uniquement si les données
     * contractuelles ont réellement changé.
     */
    @Transactional
    public Contrat update(Integer id, Contrat details) {
        Contrat c = getById(id);

        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) {
            throw new AccessDeniedException("Accès interdit");
        }

        if (hasRole(auth, "GESTIONNAIRE")
                && (!List.of("BROUILLON", "EN_ATTENTE", "REJETE").contains(c.getStatut())
                || !List.of("BROUILLON", "EN_ATTENTE", "REJETE").contains(details.getStatut()))) {

            throw new AccessDeniedException(
                    "Le gestionnaire ne peut modifier que le contenu d'un brouillon, "
                    + "d'un contrat en attente ou rejeté."
            );
        }

        if (details.getReference() == null || details.getReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Le numéro de contrat est obligatoire"
            );
        }

        if (!Objects.equals(c.getStatut(), details.getStatut())) {
            throw new AccessDeniedException(
                    "Le changement de statut doit passer par l’action métier correspondante."
            );
        }

        if (!c.getReference().equals(details.getReference())
                && contratRepo.existsByReference(details.getReference())) {
            throw new RuntimeException("Référence déjà existante");
        }

        // Capture de l'état AVANT toute modification.
        Map<String, Object> ancienneVersion = snapshot(c);
        String oldValue = auditSummary(c);

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
        c.setRenouvellementAutomatique(details.getRenouvellementAutomatique());
        c.setJoursPreavis(details.getJoursPreavis());

        Integer responsableId = hasRole(auth, "GESTIONNAIRE")
                ? currentUserId(auth)
                : details.getResponsableId();

        c.setResponsableId(responsableId);
        c.setService(serviceResponsable(responsableId));
        c.setTypeId(details.getTypeId());

        Contrat saved = contratRepo.save(c);

        // Capture de l'état APRÈS modification.
        Map<String, Object> nouvelleVersion = snapshot(saved);

        // Pas de nouvelle version si aucune donnée n'a changé.
        if (!ancienneVersion.equals(nouvelleVersion)) {
            creerVersion(saved, currentUserId(auth), null);
        }

        journauxAuditService.record(
                currentUserId(auth),
                saved.getId(),
                saved.getReference(),
                "CONTRAT_MODIFIE",
                oldValue,
                auditSummary(saved)
        );

        notifyIfNoValidationRule(saved);

        return saved;
    }

    /**
     * Construit une copie des informations contractuelles.
     * Cette copie est enregistrée dans versions_contrat.
     */
    private Map<String, Object> snapshot(Contrat c) {
        Map<String, Object> contenu = new LinkedHashMap<>();

        contenu.put("reference", c.getReference());
        contenu.put("objet", c.getObjet());
        contenu.put("description", c.getDescription());
        contenu.put("clauses", c.getClauses());
        contenu.put("montant", c.getMontant());
        contenu.put("devise", c.getDevise());
        contenu.put("dateSignature", c.getDateSignature());
        contenu.put("dateDebut", c.getDateDebut());
        contenu.put("dateFin", c.getDateFin());
        contenu.put("dureeMois", c.getDureeMois());
        contenu.put("statut", c.getStatut());
        contenu.put("service", c.getService());
        contenu.put("renouvellementAutomatique", c.getRenouvellementAutomatique());
        contenu.put("joursPreavis", c.getJoursPreavis());
        contenu.put("responsableId", c.getResponsableId());
        contenu.put("typeId", c.getTypeId());

        return contenu;
    }

    /**
     * Enregistre une version avec un numéro automatiquement incrémenté.
     */
    private void creerVersion(Contrat contrat, Integer utilisateurId, Long avenantId) {

        int numeroVersion = versionsRepo
                .findTopByContratIdOrderByVersionDesc(contrat.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        VersionsContrat version = VersionsContrat.builder()
                .contratId(contrat.getId())
                .version(numeroVersion)
                .contenu(snapshot(contrat))
                .creePar(utilisateurId)
                .avenantId(avenantId)
                .build();

        versionsRepo.save(version);
    }

    private String serviceResponsable(Integer responsableId) {
        if (responsableId == null) {
            throw new IllegalArgumentException(
                    "Le responsable du contrat est obligatoire."
            );
        }

        return utilisateursRepo.findById(responsableId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Le responsable du contrat est introuvable."
                ))
                .getService();
    }

    private void notifyIfNoValidationRule(Contrat contract) {
        if (!reglesValidationService
                .getCircuit(contract.getTypeId(), contract.getMontant())
                .isEmpty()) {
            return;
        }

        String amount = contract.getMontant() == null
                ? "non défini"
                : contract.getMontant().toPlainString();

        String message = "Aucune règle active ne couvre le type de contrat #"
                + contract.getTypeId()
                + " et le montant " + amount + " " + contract.getDevise()
                + " du contrat " + contract.getReference()
                + ". Le contrat reste en brouillon jusqu'à la configuration d'un seuil.";

        notificationsService.notifyMissingRuleForContract(
                contract.getId(),
                "Aucune règle de validation applicable",
                message
        );
    }

    @Transactional
    public void delete(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) {
            throw new AccessDeniedException("Accès interdit");
        }

        if (hasRole(auth, "GESTIONNAIRE")
                && !"BROUILLON".equals(contract.getStatut())) {
            throw new AccessDeniedException(
                    "Seuls les brouillons peuvent être supprimés par un gestionnaire."
            );
        }

        journauxAuditService.record(
                currentUserId(auth),
                contract.getId(),
                contract.getReference(),
                "CONTRAT_SUPPRIME",
                auditSummary(contract),
                null
        );

        contratRepo.delete(contract);
    }

    @Transactional
    public Contrat sign(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) {
            throw new AccessDeniedException(
                    "Signature réservée au gestionnaire ou à l'administrateur."
            );
        }

        if (!"APPROUVE".equals(contract.getStatut())) {
            throw new IllegalArgumentException(
                    "Seul un contrat entièrement approuvé peut être marqué signé."
            );
        }

        var signedPdf = documentsRepo
                .findByContratIdOrderByDateImportDesc(contract.getId())
                .stream()
                .filter(document -> "CONTRAT_SIGNE".equals(document.getCategorie()))
                .filter(document -> java.nio.file.Files.isRegularFile(
                        java.nio.file.Paths.get(document.getChemin())
                ))
                .findFirst();

        if (signedPdf.isEmpty()) {
            throw new IllegalArgumentException(
                    "Déposez d'abord le PDF du contrat signé dans les documents du contrat."
            );
        }

        String old = auditSummary(contract);

        contract.setStatut("SIGNE");
        contract.setDateSignature(LocalDate.now());

        Contrat saved = contratRepo.save(contract);

        journauxAuditService.record(
                currentUserId(auth),
                saved.getId(),
                saved.getReference(),
                "CONTRAT_SIGNE",
                old,
                auditSummary(saved)
        );

        return saved;
    }

    @Transactional
    public Contrat terminate(Integer id) {
        Contrat contract = getById(id);
        Authentication auth = currentAuth();

        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "GESTIONNAIRE")) {
            throw new AccessDeniedException(
                    "Résiliation réservée au gestionnaire ou à l'administrateur."
            );
        }

        if (!List.of("SIGNE", "ACTIF").contains(contract.getStatut())) {
            throw new IllegalArgumentException(
                    "Seul un contrat signé ou actif peut être résilié."
            );
        }

        if (obligationsContratRepository.existsByContratIdAndStatut(
                contract.getId(), "EN_COURS")) {
            throw new IllegalStateException(
                    "Clôturez ou annulez les obligations en cours avant de résilier ce contrat."
            );
        }

        String old = auditSummary(contract);

        contract.setStatut("RESILIE");
        contract.setDateArchivage(java.time.LocalDateTime.now());

        Contrat saved = contratRepo.save(contract);

        journauxAuditService.record(
                currentUserId(auth),
                saved.getId(),
                saved.getReference(),
                "CONTRAT_RESILIE",
                old,
                auditSummary(saved)
        );

        String message = "Le contrat " + saved.getReference()
                + " a été résilié par son responsable.";

        notificationsService.notifyLifecycleDate(
                saved.getResponsableId(),
                saved.getId(),
                java.time.LocalDate.now(),
                "Contrat résilié",
                message,
                "RESILIATION"
        );

        notificationsService.notifyRoleByEmail(
                Utilisateurs.Role.ADMIN,
                saved.getId(),
                "Contrat résilié",
                message,
                "RESILIATION"
        );

        notificationsService.notifyRoleByEmail(
                Utilisateurs.Role.JURIDIQUE,
                saved.getId(),
                "Contrat résilié",
                message,
                "RESILIATION"
        );

        return saved;
    }

    private void assertCanAccess(Contrat contract) {
        Authentication auth = currentAuth();

        if (hasRole(auth, "ADMIN") || hasRole(auth, "JURIDIQUE")) {
            return;
        }

        if (hasRole(auth, "GESTIONNAIRE")
                && Objects.equals(contract.getResponsableId(), currentUserId(auth))) {
            return;
        }

        if (hasRole(auth, "VALIDEUR")) {
            Integer userId = currentUserId(auth);

            List<String> validatorRoles = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring("ROLE_".length()))
                    .filter("VALIDEUR"::equals)
                    .toList();

            boolean assigned = validationsRepo.findByContratId(contract.getId())
                    .stream()
                    .anyMatch(v ->
                            validatorRoles.contains(v.getRoleRequis())
                            && (Objects.equals(v.getValideurId(), userId)
                            || (v.getValideurId() == null
                            && "EN_ATTENTE".equals(v.getDecision())))
                    );

            if (assigned) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Ce contrat ne relève pas de votre périmètre."
        );
    }

    private Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Integer)) {
            throw new AccessDeniedException("Authentification requise");
        }

        return auth;
    }

    private Integer currentUserId(Authentication auth) {
        return (Integer) auth.getPrincipal();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private String auditSummary(Contrat contract) {
        return "référence=" + contract.getReference()
                + "; statut=" + contract.getStatut()
                + "; objet=" + contract.getObjet()
                + "; responsableId=" + contract.getResponsableId()
                + "; montant=" + contract.getMontant();
    }
}
