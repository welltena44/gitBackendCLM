package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ReglesValidationRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReglesValidationServiceTest {

    @Mock
    private ReglesValidationRepository repository;
    @Mock
    private ContratRepository contratRepository;
    @Mock
    private UtilisateursRepository usersRepository;
    @Mock
    private NotificationsService notificationsService;
    @InjectMocks
    private ReglesValidationService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCreatesWorkflowWithoutPreselectedThreshold() {
        authenticate("ADMIN");
        when(repository.save(any(ReglesValidation.class))).thenAnswer(invocation -> {
            ReglesValidation saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });
        ReglesValidation requested = rule(1L, "EN_ATTENTE_SEUIL");
        requested.setMontantMinimum(new BigDecimal("500"));

        ReglesValidation saved = service.create(requested);

        assertEquals(new BigDecimal("0"), saved.getMontantMinimum());
        assertEquals(null, saved.getMontantMaximum());
        assertEquals("EN_ATTENTE_SEUIL", saved.getStatut());
        verify(notificationsService).notifyRuleRole(Utilisateurs.Role.JURIDIQUE, 15L,
                "Circuit de validation à compléter",
                "L’ADMIN a créé une règle de niveau 1. Définissez son seuil financier.");
    }

    @Test
    void legalDefinesThresholdAndNotifiesAdminForConfirmation() {
        authenticate("JURIDIQUE");
        ReglesValidation existing = rule(15L, "EN_ATTENTE_SEUIL");
        when(repository.findById(15L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ReglesValidation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReglesValidation threshold = new ReglesValidation();
        threshold.setMontantMinimum(new BigDecimal("50000000"));
        threshold.setMontantMaximum(new BigDecimal("100000000"));

        ReglesValidation saved = service.update(15L, threshold);

        assertEquals(new BigDecimal("50000000"), saved.getMontantMinimum());
        assertEquals("EN_ATTENTE_CONFIRMATION", saved.getStatut());
        verify(notificationsService).notifyRuleRole(Utilisateurs.Role.ADMIN, 15L,
                "Seuil de validation à confirmer",
                "Le Juridique a défini le seuil de la règle de niveau 1. Confirmez son activation.");
    }

    @Test
    void adminConfirmsThresholdAndActivatesRule() {
        authenticate("ADMIN");
        ReglesValidation existing = rule(15L, "EN_ATTENTE_CONFIRMATION");
        existing.setMontantMinimum(new BigDecimal("50000000"));
        when(repository.findById(15L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ReglesValidation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReglesValidation saved = service.confirm(15L);

        assertEquals("ACTIVE", saved.getStatut());
        verify(notificationsService).notifyRuleRole(Utilisateurs.Role.JURIDIQUE, 15L,
                "Règle de validation activée",
                "L’ADMIN a confirmé et activé la règle de niveau 1.");
    }

    @Test
    void adminActivationNotifiesManagerOfPreviouslyUnmatchedContract() {
        authenticate("ADMIN");
        ReglesValidation existing = rule(15L, "EN_ATTENTE_CONFIRMATION");
        existing.setMontantMinimum(new BigDecimal("50000000"));
        Contrat contract = new Contrat();
        contract.setId(9);
        contract.setReference("CON-09");
        contract.setTypeId(15L);
        contract.setMontant(new BigDecimal("75000000"));
        contract.setResponsableId(22);
        when(repository.findById(15L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ReglesValidation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contratRepository.findByStatutIn(List.of("BROUILLON", "EN_ATTENTE"))).thenReturn(List.of(contract));
        when(repository.findCircuitApplicable(eq(15L), eq(contract.getMontant())))
                .thenReturn(List.of(), List.of(existing));

        service.confirm(15L);

        verify(notificationsService).notifyUser(22, 9, "Seuil de validation disponible",
                "Le seuil de validation du contrat CON-09 est maintenant actif. Vous pouvez le soumettre au circuit de validation.",
                "INFO");
    }

    @Test
    void legalCannotCreateWorkflow() {
        authenticate("JURIDIQUE");

        assertThrows(AccessDeniedException.class, () -> service.create(rule(1L, "EN_ATTENTE_SEUIL")));
    }

    private ReglesValidation rule(Long typeId, String status) {
        ReglesValidation rule = new ReglesValidation();
        rule.setId(15L);
        rule.setTypeContratId(typeId);
        rule.setNiveau(1);
        rule.setRoleRequis("VALIDEUR");
        rule.setMontantMinimum(BigDecimal.ZERO);
        rule.setStatut(status);
        return rule;
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                1, "test", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}