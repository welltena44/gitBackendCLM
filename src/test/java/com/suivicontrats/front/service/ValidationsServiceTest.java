package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.repository.ContratRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationsServiceTest {

        @Mock
        private ValidationsRepository validationsRepository;
        @Mock
        private ContratRepository contratRepository;
        @Mock
        private UtilisateursRepository utilisateursRepository;
        @Mock
        private NotificationsService notificationsService;
        @Mock
        private JournauxAuditService journauxAuditService;
        @Mock
        private ReglesValidationService reglesValidationService;
        @InjectMocks
        private ValidationsService service;

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void notifiesAdminAndLegalWhenSubmissionHasNoMatchingRule() {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                22, "test", List.of(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))));
                Contrat contract = new Contrat();
                contract.setId(9);
                contract.setReference("CON-02");
                contract.setTypeId(1L);
                contract.setMontant(new BigDecimal("75000000"));
                contract.setDevise("MGA");
                contract.setStatut("BROUILLON");
                contract.setResponsableId(22);
                when(contratRepository.findById(9)).thenReturn(Optional.of(contract));
                when(validationsRepository.findByContratId(9)).thenReturn(List.of());
                when(validationsRepository.findTopByContratIdOrderByCycleDesc(9)).thenReturn(Optional.empty());
                when(reglesValidationService.getCircuit(1L, contract.getMontant())).thenReturn(List.of());

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> service.genererCircuit(9, 22));

                assertEquals("Aucune règle de validation ne couvre le type et le montant de ce contrat.",
                                exception.getMessage());
                verify(notificationsService).notifyMissingRuleForContract(9,
                                "Aucune règle de validation applicable",
                                "Aucune règle active ne couvre le type et le montant du contrat CON-02 (75000000 MGA). "
                                                + "Le contrat reste en brouillon jusqu'à la configuration d'un seuil.");
        }
}