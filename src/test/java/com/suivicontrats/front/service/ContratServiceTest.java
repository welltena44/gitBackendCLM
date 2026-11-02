package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.DocumentsContratRepository;
import com.suivicontrats.front.repository.ObligationsContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ContratServiceTest {

    @Mock
    private ContratRepository contratRepository;
    @Mock
    private ValidationsRepository validationsRepository;
    @Mock
    private DocumentsContratRepository documentsRepository;
    @Mock
    private JournauxAuditService journauxAuditService;
    @Mock
    private ReglesValidationService reglesValidationService;
    @Mock
    private NotificationsService notificationsService;
    @Mock
    private UtilisateursRepository utilisateursRepository;
    @Mock
    private ObligationsContratRepository obligationsContratRepository;
    @InjectMocks
    private ContratService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAdminAndLegalAlertsWhenNoRuleMatchesContract() {
        authenticate();
        Contrat requested = new Contrat();
        requested.setReference("CON-01");
        requested.setObjet("Prestation");
        requested.setMontant(new BigDecimal("75000000"));
        requested.setDevise("MGA");
        requested.setTypeId(1L);
        requested.setStatut("BROUILLON");
        when(contratRepository.existsByReference("CON-01")).thenReturn(false);
        when(utilisateursRepository.findById(22)).thenReturn(Optional.of(manager("Gestion")));
        when(contratRepository.save(any(Contrat.class))).thenAnswer(invocation -> {
            Contrat saved = invocation.getArgument(0);
            saved.setId(9);
            return saved;
        });
        when(reglesValidationService.getCircuit(eq(1L), any(BigDecimal.class))).thenReturn(List.of());

        service.create(requested);

        assertEquals("Gestion", requested.getService());
        verify(notificationsService).notifyMissingRuleForContract(
                eq(9), eq("Aucune règle de validation applicable"),
                contains("type de contrat #1 et le montant 75000000 MGA du contrat CON-01"));
    }

    @Test
    void updatesAdminAndLegalAlertsWhenNoRuleMatchesContract() {
        authenticate();
        Contrat existing = contract();
        Contrat details = contract();
        details.setService("Autre service");
        when(contratRepository.findById(9)).thenReturn(Optional.of(existing));
        when(utilisateursRepository.findById(22)).thenReturn(Optional.of(manager("Gestion")));
        when(contratRepository.save(any(Contrat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reglesValidationService.getCircuit(eq(1L), any(BigDecimal.class))).thenReturn(List.of());

        service.update(9, details);

        assertEquals("Gestion", existing.getService());
        verify(notificationsService).notifyMissingRuleForContract(
                eq(9), eq("Aucune règle de validation applicable"),
                contains("type de contrat #1 et le montant 75000000 MGA du contrat CON-09"));
    }

    @Test
    void managerCanEditRejectedContractWithoutChangingItsStatus() {
        authenticate();
        Contrat existing = contract();
        existing.setStatut("REJETE");
        Contrat details = contract();
        details.setStatut("REJETE");
        details.setObjet("Prestation corrigée");
        when(contratRepository.findById(9)).thenReturn(Optional.of(existing));
        when(utilisateursRepository.findById(22)).thenReturn(Optional.of(manager("Gestion")));
        when(contratRepository.save(any(Contrat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reglesValidationService.getCircuit(eq(1L), any(BigDecimal.class))).thenReturn(List.of());

        Contrat updated = service.update(9, details);

        assertEquals("Prestation corrigée", updated.getObjet());
        assertEquals("REJETE", updated.getStatut());
    }

    @Test
    void cannotTerminateContractWithOpenObligations() {
        authenticate();
        Contrat existing = contract();
        existing.setStatut("ACTIF");
        when(contratRepository.findById(9)).thenReturn(Optional.of(existing));
        when(obligationsContratRepository.existsByContratIdAndStatut(9, "EN_COURS")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.terminate(9));
    }

    private Contrat contract() {
        Contrat contract = new Contrat();
        contract.setId(9);
        contract.setReference("CON-09");
        contract.setObjet("Prestation");
        contract.setMontant(new BigDecimal("75000000"));
        contract.setDevise("MGA");
        contract.setDateDebut(LocalDate.of(2026, 1, 1));
        contract.setDateFin(LocalDate.of(2027, 1, 1));
        contract.setStatut("BROUILLON");
        contract.setResponsableId(22);
        contract.setTypeId(1L);
        return contract;
    }

    private Utilisateurs manager(String service) {
        Utilisateurs manager = new Utilisateurs();
        manager.setId(22);
        manager.setService(service);
        return manager;
    }

    private void authenticate() {
        var authentication = new UsernamePasswordAuthenticationToken(
                22, "test", List.of(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}