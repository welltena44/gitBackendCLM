package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.VersionsContratRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcheancesSchedulerTest {
    @Mock
    private ContratRepository contrats;
    @Mock
    private ParametresAlertesRepository alertes;
    @Mock
    private NotificationsService notifications;
    @Mock
    private JournauxAuditService audit;
    @Mock
    private ValidationsRepository validations;
    @Mock
    private UtilisateursRepository utilisateurs;
    @Mock
    private VersionsContratRepository versions;
    @Mock
    private ObligationsContratService obligations;
    @InjectMocks
    private EcheancesScheduler scheduler;

    @Test
    void renewsAtEndDateAndArchivesThePreviousVersion() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        Contrat contract = contract(today, "ACTIF");
        contract.setRenouvellementAutomatique(true);
        contract.setDureeMois(12);
        when(contrats.findAll()).thenReturn(List.of(contract));
        when(alertes.findByActifTrueOrderByJoursAvantAsc()).thenReturn(List.of());
        when(versions.findTopByContratIdOrderByVersionDesc(9)).thenReturn(Optional.empty());
        when(validations.findByDecision("EN_ATTENTE")).thenReturn(List.of());

        scheduler.traiterEcheances(today);

        assertEquals(today.plusMonths(12), contract.getDateFin());
        assertEquals("ACTIF", contract.getStatut());
        ArgumentCaptor<VersionsContrat> version = ArgumentCaptor.forClass(VersionsContrat.class);
        verify(versions).save(version.capture());
        assertEquals(today, version.getValue().getContenu().get("dateFin"));
        verify(notifications).notifyLifecycleDate(22, 9, today,
                "Contrat renouvelé automatiquement",
                "Le contrat CON-09 a été prolongé jusqu’au " + today.plusMonths(12)
                        + ". La version précédente a été archivée.",
                "RENOUVELLEMENT");
    }

    @Test
    void expiresButDoesNotArchiveWhileAnObligationIsOpen() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        Contrat contract = contract(today.minusDays(1), "ACTIF");
        when(contrats.findAll()).thenReturn(List.of(contract));
        when(alertes.findByActifTrueOrderByJoursAvantAsc()).thenReturn(List.of());
        when(obligations.hasOpenObligations(9)).thenReturn(true);
        when(validations.findByDecision("EN_ATTENTE")).thenReturn(List.of());

        scheduler.traiterEcheances(today);

        assertEquals("EXPIRE", contract.getStatut());
        assertNull(contract.getDateArchivage());
        verify(obligations).hasOpenObligations(9);
    }

    @Test
    void requestsHumanDecisionOnTheNoticeDeadlineWithoutTerminatingContract() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        Contrat contract = contract(today.plusDays(30), "ACTIF");
        contract.setJoursPreavis(30);
        when(contrats.findAll()).thenReturn(List.of(contract));
        when(alertes.findByActifTrueOrderByJoursAvantAsc()).thenReturn(List.of());
        when(validations.findByDecision("EN_ATTENTE")).thenReturn(List.of());

        scheduler.traiterEcheances(today);

        assertEquals("ACTIF", contract.getStatut());
        verify(notifications).notifyLifecycleDate(22, 9, today,
                "Décision humaine requise : préavis contractuel",
                "Le préavis du contrat CON-09 arrive à échéance le " + today
                        + ". Examinez le contrat et confirmez manuellement une résiliation si elle est souhaitée. "
                        + "La résiliation ne sera pas automatique.",
                "PREAVIS");
    }

    @Test
    void sendsOneReminderToPendingValidationAfterThreeDays() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        Contrat contract = contract(today.plusDays(20), "ACTIF");
        Validations validation = Validations.builder()
                .id(51L).contratId(9).valideurId(13).niveau(1).roleRequis("VALIDEUR")
                .decision("EN_ATTENTE").dateSoumission(today.minusDays(3).atStartOfDay()).build();
        when(contrats.findAll()).thenReturn(List.of(contract));
        when(alertes.findByActifTrueOrderByJoursAvantAsc()).thenReturn(List.of());
        when(validations.findByDecision("EN_ATTENTE")).thenReturn(List.of(validation));
        when(contrats.findById(9)).thenReturn(Optional.of(contract));
        when(utilisateurs.findById(13)).thenReturn(Optional.empty());

        scheduler.traiterEcheances(today);

        verify(notifications).notifyRoleByEmail(
                com.suivicontrats.front.entity.Utilisateurs.Role.VALIDEUR, 9,
                "Validation réattribuée",
                "Le valideur précédemment désigné est désactivé. La validation de niveau 1 est ouverte aux utilisateurs actifs du rôle VALIDEUR.",
                "VALIDATION");
        verify(notifications).notifyRoleByEmail(
                com.suivicontrats.front.entity.Utilisateurs.Role.VALIDEUR, 9,
                "Rappel : validation en attente",
                "La validation de niveau 1 du contrat CON-09 est en attente depuis 3 jour(s).",
                "VALIDATION");
        assertNull(validation.getValideurId());
        verify(validations, times(2)).save(validation);
    }

    private Contrat contract(LocalDate end, String status) {
        Contrat contract = new Contrat();
        contract.setId(9);
        contract.setReference("CON-09");
        contract.setObjet("Prestation");
        contract.setMontant(new BigDecimal("100"));
        contract.setDevise("MGA");
        contract.setDateDebut(end.minusMonths(1));
        contract.setDateFin(end);
        contract.setDureeMois(1);
        contract.setRenouvellementAutomatique(false);
        contract.setStatut(status);
        contract.setResponsableId(22);
        return contract;
    }
}
