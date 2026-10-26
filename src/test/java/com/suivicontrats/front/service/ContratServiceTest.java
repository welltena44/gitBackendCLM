package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.DocumentsContratRepository;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(contratRepository.save(any(Contrat.class))).thenAnswer(invocation -> {
            Contrat saved = invocation.getArgument(0);
            saved.setId(9);
            return saved;
        });
        when(reglesValidationService.getCircuit(eq(1L), any(BigDecimal.class))).thenReturn(List.of());

        service.create(requested);

        String message = "Aucune règle active ne couvre le type et le montant du contrat CON-01 (75000000 MGA). "
                + "Le contrat reste en brouillon jusqu'à la configuration d'un seuil.";
        verify(notificationsService).notifyContractRole(Utilisateurs.Role.ADMIN, 9,
                "Aucune règle de validation applicable", message, "INFO");
        verify(notificationsService).notifyContractRole(Utilisateurs.Role.JURIDIQUE, 9,
                "Aucune règle de validation applicable", message, "INFO");
    }

    private void authenticate() {
        var authentication = new UsernamePasswordAuthenticationToken(
                22, "test", List.of(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}