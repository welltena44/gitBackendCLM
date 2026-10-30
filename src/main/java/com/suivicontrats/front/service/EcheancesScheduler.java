package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EcheancesScheduler {
    private final ContratRepository contrats;
    private final ParametresAlertesRepository alertes;
    private final NotificationsService notifications;
    private final JournauxAuditService audit;

    @Scheduled(cron = "0 0 7 * * *", zone = "Indian/Antananarivo")
    @Transactional
    public void notifierEcheances() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo"));
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
            if (("SIGNE".equals(contract.getStatut()) || "ACTIF".equals(contract.getStatut()))
                    && contract.getDateFin().isBefore(today)) {
                String old = contract.getStatut();
                contract.setStatut("EXPIRE");
                contrats.save(contract);
                audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_EXPIRE", "statut=" + old,
                        "statut=EXPIRE");
                continue;
            }
            if (contract.getResponsableId() == null
                    || !List.of("SIGNE", "ACTIF", "APPROUVE").contains(contract.getStatut()))
                continue;
            // L'action doit être déclenchée avant la fin du contrat, à la date limite de préavis.
            LocalDate dateLimitePreavis = contract.getDateFin().minusDays(
                    Math.max(0, contract.getJoursPreavis() == null ? 0 : contract.getJoursPreavis()));
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, dateLimitePreavis);
            for (ParametresAlertes rule : rules) {
                if (days < 0 || days > rule.getJoursAvant())
                    continue;
                String nature = Boolean.TRUE.equals(contract.getRenouvellementAutomatique()) ? "renouvellement"
                        : "résiliation ou non-renouvellement";
                notifications.notifyDeadline(contract.getResponsableId(), contract.getId(), rule.getId(),
                        "Préavis à traiter dans " + days + " jour(s)",
                        "Le préavis du contrat " + contract.getReference() + " doit être traité au plus tard le "
                                + dateLimitePreavis + ". Date de fin du contrat : " + contract.getDateFin()
                                + ". Action à prévoir : " + nature + ". Préavis contractuel : "
                                + contract.getJoursPreavis() + " jour(s).");
            }
        }
    }
}
