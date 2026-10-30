
package com.suivicontrats.front.service;

import com.suivicontrats.front.dto.LoginRequest;
import com.suivicontrats.front.dto.LoginResponse;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import com.suivicontrats.front.service.JournauxAuditService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateursRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final JournauxAuditService auditService;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    public LoginResponse login(LoginRequest req) {
        String matricule = req == null ? null : req.getMatricule();
        String password = req == null ? null : req.getMotDePasse();
        if (matricule == null || matricule.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Matricule ou mot de passe incorrect.");
        }

        final Integer matriculeSaisi;
        try {
            matriculeSaisi = Integer.valueOf(matricule.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Matricule ou mot de passe incorrect.");
        }

        Utilisateurs u = repo.findByMatricule(matriculeSaisi).orElse(null);
        // Message identique pour un matricule inexistant et un mot de passe incorrect.
        if (u == null || !Boolean.TRUE.equals(u.getActif())) {
            throw new IllegalArgumentException("Matricule ou mot de passe incorrect.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (u.getVerrouilleJusqua() != null && u.getVerrouilleJusqua().isAfter(now)) {
            throw new org.springframework.security.authentication.LockedException(
                    "Compte temporairement verrouillé. Réessayez dans 15 minutes.");
        }
        if (u.getVerrouilleJusqua() != null && !u.getVerrouilleJusqua().isAfter(now)) {
            u.setVerrouilleJusqua(null);
            u.setTentativesEchec(0);
        }

        if (!encoder.matches(password, u.getMotDePasse())) {
            int attempts = (u.getTentativesEchec() == null ? 0 : u.getTentativesEchec()) + 1;
            u.setTentativesEchec(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                u.setVerrouilleJusqua(now.plusMinutes(LOCK_MINUTES));
                u.setTentativesEchec(0);
            }
            repo.save(u);
            throw new IllegalArgumentException("Matricule ou mot de passe incorrect.");
        }

        u.setTentativesEchec(0);
        u.setVerrouilleJusqua(null);
        repo.save(u);
        auditService.record(u.getId(), null, null, "CONNEXION", null,
                "Connexion réussie pour le matricule " + u.getMatricule());

        return LoginResponse.builder()
                .id(u.getId()).matricule(u.getMatricule()).nom(u.getNom()).prenom(u.getPrenom())
                .email(u.getEmail()).role(u.getRole().name()).service(u.getService()).actif(u.getActif())
                .changementMotDePasseRequis(u.getChangementMotDePasseRequis())
                .token(jwtService.issue(u.getId())).message("Connexion réussie").build();
    }
}
