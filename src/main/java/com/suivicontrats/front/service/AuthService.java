
package com.suivicontrats.front.service;

import com.suivicontrats.front.dto.LoginRequest;
import com.suivicontrats.front.dto.LoginResponse;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.UtilisateursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateursRepository repo;
    private final PasswordEncoder encoder;

    public LoginResponse login(LoginRequest req) {

        // Recherche par matricule
        String matricule = req.getMatricule();

        if (matricule == null || matricule.trim().isEmpty()) {
            throw new RuntimeException("Veuillez saisir votre matricule");
        }

        Integer matriculeSaisi;
        try {
            matriculeSaisi = Integer.valueOf(matricule.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Matricule invalide");
        }

        Utilisateurs u = repo.findByMatricule(matriculeSaisi)
            .orElseThrow(() ->
                new RuntimeException("Matricule non trouvé : " + matricule)
            );

        if (!Boolean.TRUE.equals(u.getActif())) {
            throw new RuntimeException("Compte désactivé");
        }

        if (req.getMotDePasse() == null ||
            !encoder.matches(req.getMotDePasse(), u.getMotDePasse())) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        return LoginResponse.builder()
            .id(u.getId())
            .matricule(u.getMatricule())
            .nom(u.getNom())
            .prenom(u.getPrenom())
            .email(u.getEmail())
            .role(u.getRole().name())
            .service(u.getService())
            .actif(u.getActif())
            .message("Connexion réussie")
            .build();
    }
}
