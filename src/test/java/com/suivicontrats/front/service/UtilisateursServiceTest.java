package com.suivicontrats.front.service;

import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilisateursServiceTest {

    @Mock
    private UtilisateursRepository utilisateursRepository;
    @Mock
    private ContratRepository contratRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @InjectMocks
    private UtilisateursService service;

    @Test
    void profileServiceChangeSynchronizesResponsibleContracts() {
        Utilisateurs manager = manager("Ancien service");
        when(utilisateursRepository.findById(22)).thenReturn(Optional.of(manager));
        when(utilisateursRepository.findByEmailIgnoreCase("gestionnaire@example.test"))
                .thenReturn(Optional.empty());
        when(utilisateursRepository.save(manager)).thenReturn(manager);
        ProfilRequest request = new ProfilRequest();
        request.setNom("Nom");
        request.setPrenom("Prenom");
        request.setEmail("gestionnaire@example.test");
        request.setService("  Nouveau service  ");

        service.updateProfile(22, request);

        assertEquals("Nouveau service", manager.getService());
        verify(contratRepository).updateServiceByResponsableId(22, "Nouveau service");
    }

    @Test
    void adminServiceChangeSynchronizesResponsibleContracts() {
        Utilisateurs manager = manager("Ancien service");
        when(utilisateursRepository.findById(22)).thenReturn(Optional.of(manager));
        when(utilisateursRepository.save(manager)).thenReturn(manager);
        Utilisateurs details = new Utilisateurs();
        details.setEmail("gestionnaire@example.test");
        details.setService("Nouveau service");

        service.update(22, details);

        verify(contratRepository).updateServiceByResponsableId(22, "Nouveau service");
    }

    private Utilisateurs manager(String service) {
        Utilisateurs manager = new Utilisateurs();
        manager.setId(22);
        manager.setNom("Nom");
        manager.setPrenom("Prenom");
        manager.setEmail("gestionnaire@example.test");
        manager.setRole(Utilisateurs.Role.GESTIONNAIRE);
        manager.setService(service);
        manager.setActif(true);
        manager.setMotDePasse("hash");
        return manager;
    }
}
