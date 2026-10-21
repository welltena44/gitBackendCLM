package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class TwoFactorController {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final UtilisateursRepository utilisateursRepository;

    @GetMapping("/generate/{matricule}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> generate2FA(@PathVariable String matricule, Authentication authentication) {
        Utilisateurs user = utilisateursRepository.findByMatricule(Integer.valueOf(matricule))
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (!user.getId().equals(authentication.getPrincipal())) return ResponseEntity.status(403).body(Map.of("message", "Accès interdit."));

        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        // Enregistre le secret 2FA provisoirement pour cet utilisateur
        user.setSecret2fa(secret);
        utilisateursRepository.save(user);

        // Génère l'URL du QR Code Google Authenticator
        String qrCodeUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL("SPAT-CLM", user.getEmail(), key);

        return ResponseEntity.ok(Map.of(
            "secret", secret,
            "qrCodeUrl", qrCodeUrl
        ));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> verify2FA(@RequestBody Map<String, Object> request, Authentication authentication) {
        String matricule = (String) request.get("matricule");
        int code = Integer.parseInt(request.get("code").toString());

        Utilisateurs user = utilisateursRepository.findByMatricule(Integer.valueOf(matricule))
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (!user.getId().equals(authentication.getPrincipal())) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Accès interdit."));

        if (user.getSecret2fa() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Aucun secret 2FA généré."));
        }

        boolean isAuthorized = gAuth.authorize(user.getSecret2fa(), code);

        if (isAuthorized) {
            user.setEnabled2fa(true);
            utilisateursRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "2FA activé avec succès !"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Code de validation incorrect."));
        }
    }
}
