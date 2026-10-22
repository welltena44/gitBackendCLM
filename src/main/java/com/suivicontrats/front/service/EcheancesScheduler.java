package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    public void notifierEcheances() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo"));
        List<ParametresAlertes> rules = alertes.findByActifTrueOrderByJoursAvantAsc();
        for (Contrat contract : contrats.findAll()) {
            if (contract.getDateFin() == null) continue;
            if ("SIGNE".equals(contract.getStatut()) && contract.getDateDebut() != null && !contract.getDateDebut().isAfter(today)) {
                String old = contract.getStatut(); contract.setStatut("ACTIF"); contrats.save(contract);
                audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_ACTIF", "statut=" + old, "statut=ACTIF");
            }
            if (("SIGNE".equals(contract.getStatut()) || "ACTIF".equals(contract.getStatut())) && contract.getDateFin().isBefore(today)) {
                String old = contract.getStatut(); contract.setStatut("EXPIRE"); contrats.save(contract);
                audit.record(null, contract.getId(), contract.getReference(), "CONTRAT_EXPIRE", "statut=" + old, "statut=EXPIRE");
                continue;
            }
            if (contract.getResponsableId() == null || !List.of("SIGNE", "ACTIF", "APPROUVE").contains(contract.getStatut())) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getDateFin());
            for (ParametresAlertes rule : rules) {
                if (days < 0 || days > rule.getJoursAvant()) continue;
                String nature = Boolean.TRUE.equals(contract.getRenouvellementAutomatique()) ? "renouvellement" : "fin / résiliation";
                notifications.notifyDeadline(contract.getResponsableId(), contract.getId(), rule.getId(),
                        "Échéance contractuelle dans " + days + " jours",
                        "Le contrat " + contract.getReference() + " arrive à échéance le " + contract.getDateFin() + ". Vérifiez le " + nature + " et le préavis de " + contract.getJoursPreavis() + " jour(s).");
            }
        }
    }
}
