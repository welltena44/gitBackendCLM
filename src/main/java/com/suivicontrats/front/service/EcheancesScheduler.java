package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EcheancesScheduler {
    private static final ZoneId APP_ZONE = ZoneId.of("Indian/Antananarivo");

    private final ContratRepository contrats;
    private final ParametresAlertesRepository alertes;
    private final NotificationsService notifications;
    private final JournauxAuditService audit;
    private final ValidationsRepository validations;
    private final UtilisateursRepository utilisateurs;
    private final VersionsContratRepository versions;
    private final ObligationsContratService obligations;

    @Scheduled(cron = "0 0 7 * * *", zone = "Indian/Antananarivo")
    @Transactional
    public void notifierEcheances() {
        traiterEcheances(LocalDate.now(APP_ZONE));
    }

    void traiterEcheances(LocalDate today) {
        traiterContrats(today);
        traiterValidations(today);
    }

    private void traiterContrats(LocalDate today) {
        List<ParametresAlertes> rules = alertes.findByActifTrueOrderByJoursAvantAsc();
        for (Contrat contract : contrats.findAll()) {
            if (contract.getDateFin() == null)
                continue;
            if ("SIGNE".equals(contract.getStatut()) && contract.getDateDebut() != null
                    && !contract.getDateDebut().isAfter(today)) {
                String old = contract.getStatut();
                contract.setStatut("ACTIF");
                contrats.save(contract);
                audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_ACTIF", "statut=" + old,
                        "statut=ACTIF");
            }
            if (List.of("EXPIRE", "RESILIE").contains(contract.getStatut())) {
                archiverSiPossible(contract);
                continue;
            }

            if (List.of("SIGNE", "ACTIF").contains(contract.getStatut())
                    && !contract.getDateFin().isAfter(today)) {
                if (Boolean.TRUE.equals(contract.getRenouvellementAutomatique())
                        && contract.getDureeMois() != null && contract.getDureeMois() > 0) {
                    renouveler(contract);
                    continue;
                }
                expirer(contract);
                continue;
            }

            if (contract.getResponsableId() == null
                    || !List.of("SIGNE", "ACTIF", "APPROUVE").contains(contract.getStatut()))
                continue;
            LocalDate dateLimitePreavis = contract.getDateFin().minusDays(
                    Math.max(0, contract.getJoursPreavis() == null ? 0 : contract.getJoursPreavis()));
            long days = ChronoUnit.DAYS.between(today, dateLimitePreavis);
            if (days == 0)
                notifierDecisionPreavis(contract, dateLimitePreavis);
            for (ParametresAlertes rule : rules) {
                if (days < 0 || days > rule.getJoursAvant())
                    continue;
                String nature = Boolean.TRUE.equals(contract.getRenouvellementAutomatique()) ? "renouvellement"
                        : "résiliation ou non-renouvellement";
                notifications.notifyDeadline(contract.getResponsableId(), contract.getId(), rule.getId(),
                    contract.getDateFin(),
                        "Préavis à traiter dans " + days + " jour(s)",
                        "Le préavis du contrat " + contract.getReference() + " doit être traité au plus tard le "
                                + dateLimitePreavis + ". Date de fin du contrat : " + contract.getDateFin()
                                + ". Action à prévoir : " + nature + ". Préavis contractuel : "
                                + contract.getJoursPreavis() + " jour(s).");
            }
        }
    }

    private void notifierDecisionPreavis(Contrat contract, LocalDate deadline) {
        String title = "Décision humaine requise : préavis contractuel";
        String message = "Le préavis du contrat " + contract.getReference() + " arrive à échéance le " + deadline
                + ". Examinez le contrat et confirmez manuellement une résiliation si elle est souhaitée. "
                + "La résiliation ne sera pas automatique.";
        java.util.ArrayList<Integer> recipients = new java.util.ArrayList<>();
        recipients.add(contract.getResponsableId());
        for (Utilisateurs.Role role : List.of(Utilisateurs.Role.ADMIN, Utilisateurs.Role.JURIDIQUE)) {
            utilisateurs.findByRoleAndActifTrue(role).stream()
                    .map(Utilisateurs::getId)
                    .forEach(recipients::add);
        }
        recipients.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(userId -> notifications.notifyLifecycleDate(userId, contract.getId(), deadline,
                        title, message, "PREAVIS"));
    }

    private void renouveler(Contrat contract) {
        LocalDate ancienneFin = contract.getDateFin();
        LocalDate nouvelleFin = ancienneFin.plusMonths(contract.getDureeMois());
        int nextVersion = versions.findTopByContratIdOrderByVersionDesc(contract.getId())
                .map(version -> version.getVersion() + 1).orElse(1);
        versions.save(VersionsContrat.builder()
                .contratId(contract.getId())
                .version(nextVersion)
                .contenu(snapshot(contract))
                .creePar(null)
                .build());
        contract.setDateFin(nouvelleFin);
        contract.setStatut("ACTIF");
        contrats.save(contract);
        audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_RENOUVELE",
                "dateFin=" + ancienneFin, "dateFin=" + nouvelleFin + "; version=" + nextVersion);
        String title = "Contrat renouvelé automatiquement";
        String message = "Le contrat " + contract.getReference() + " a été prolongé jusqu’au " + nouvelleFin
                + ". La version précédente a été archivée.";
        notifierAdminEtJuridique(contract, ancienneFin, title, message, "RENOUVELLEMENT");
    }

    private void expirer(Contrat contract) {
        String old = contract.getStatut();
        contract.setStatut("EXPIRE");
        contrats.save(contract);
        audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_EXPIRE", "statut=" + old,
                "statut=EXPIRE");
        String title = "Contrat arrivé à échéance";
        String message = "Le contrat " + contract.getReference() + " est arrivé à sa date de fin, le "
                + contract.getDateFin() + ".";
        notifierAdminEtJuridique(contract, contract.getDateFin(), title, message, "ECHEANCE");
        archiverSiPossible(contract);
    }

    private void archiverSiPossible(Contrat contract) {
        if (contract.getDateArchivage() != null || obligations.hasOpenObligations(contract.getId()))
            return;
        contract.setDateArchivage(LocalDateTime.now(APP_ZONE));
        contrats.save(contract);
        audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_ARCHIVE", null,
                "dateArchivage=" + contract.getDateArchivage());
    }

    private void traiterValidations(LocalDate today) {
        for (Validations validation : validations.findByDecision("EN_ATTENTE")) {
            if (validation.getDateSoumission() == null)
                continue;
            reaffecterSiDesactive(validation);
            long days = ChronoUnit.DAYS.between(validation.getDateSoumission().toLocalDate(), today);
            Contrat contract = contrats.findById(validation.getContratId()).orElse(null);
            if (contract == null)
                continue;
            String reference = contract.getReference();
            String title = "Rappel : validation en attente";
            String message = "La validation de niveau " + validation.getNiveau() + " du contrat " + reference
                    + " est en attente depuis " + days + " jour(s).";
            if (days >= 3 && days < 5 && validation.getDateRappel() == null) {
                notifierValidateur(validation, title, message);
                validation.setDateRappel(LocalDateTime.now(APP_ZONE));
                validations.save(validation);
            }
            if (days >= 5 && validation.getDateEscalade() == null) {
                String escalation = "La validation de niveau " + validation.getNiveau() + " du contrat "
                        + reference + " est en attente depuis " + days + " jour(s). Une intervention est requise.";
                notifications.notifyUserByEmail(contract.getResponsableId(), contract.getId(),
                        "Escalade : validation en retard", escalation, "VALIDATION");
                notifications.notifyRoleByEmail(Utilisateurs.Role.ADMIN, contract.getId(),
                        "Escalade : validation en retard", escalation, "VALIDATION");
                validation.setDateEscalade(LocalDateTime.now(APP_ZONE));
                validations.save(validation);
            }
        }
    }

    private void reaffecterSiDesactive(Validations validation) {
        if (validation.getValideurId() == null)
            return;
        boolean active = utilisateurs.findById(validation.getValideurId())
                .map(user -> Boolean.TRUE.equals(user.getActif()))
                .orElse(false);
        if (active)
            return;
        Integer previousId = validation.getValideurId();
        validation.setValideurId(null);
        validations.save(validation);
        notifications.notifyRoleByEmail(Utilisateurs.Role.valueOf(validation.getRoleRequis()),
                validation.getContratId(), "Validation réattribuée",
                "Le valideur précédemment désigné est désactivé. La validation de niveau "
                        + validation.getNiveau() + " est ouverte aux utilisateurs actifs du rôle "
                        + validation.getRoleRequis() + ".",
                "VALIDATION");
        audit.record(null, validation.getContratId(), null, "VALIDATION_REATTRIBUEE",
                "valideurId=" + previousId, "valideurId=null; role=" + validation.getRoleRequis());
    }

    private void notifierValidateur(Validations validation, String title, String message) {
        if (validation.getValideurId() != null) {
            notifications.notifyUserByEmail(validation.getValideurId(), validation.getContratId(),
                    title, message, "VALIDATION");
        } else {
            notifications.notifyRoleByEmail(Utilisateurs.Role.valueOf(validation.getRoleRequis()),
                    validation.getContratId(), title, message, "VALIDATION");
        }
    }

    private void notifierAdminEtJuridique(Contrat contract, LocalDate eventDate, String title, String message,
            String type) {
        notifications.notifyLifecycleDate(contract.getResponsableId(), contract.getId(), eventDate,
                title, message, type);
        notifications.notifyRoleByEmail(Utilisateurs.Role.ADMIN, contract.getId(), title, message, type);
        notifications.notifyRoleByEmail(Utilisateurs.Role.JURIDIQUE, contract.getId(), title, message, type);
    }

    private Map<String, Object> snapshot(Contrat contract) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reference", contract.getReference());
        data.put("objet", contract.getObjet());
        data.put("description", contract.getDescription());
        data.put("clauses", contract.getClauses());
        data.put("montant", contract.getMontant());
        data.put("devise", contract.getDevise());
        data.put("dateSignature", contract.getDateSignature());
        data.put("dateDebut", contract.getDateDebut());
        data.put("dateFin", contract.getDateFin());
        data.put("dureeMois", contract.getDureeMois());
        data.put("service", contract.getService());
        data.put("renouvellementAutomatique", contract.getRenouvellementAutomatique());
        data.put("joursPreavis", contract.getJoursPreavis());
        data.put("typeId", contract.getTypeId());
        data.put("statut", contract.getStatut());
        return data;
    }
}
