package com.suivicontrats.front.controller;

import com.suivicontrats.front.dto.DemandeReinitialisationDTO;
import com.suivicontrats.front.service.DemandesReinitialisationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DemandesReinitialisationController {
    private final DemandesReinitialisationService service;

    @PostMapping("/api/auth/password-reset-requests")
    public ResponseEntity<Map<String, String>> soumettre(@RequestBody Map<String, String> body) {
        Integer matricule = null;
        try {
            matricule = Integer.valueOf(body.getOrDefault("matricule", "").trim());
        } catch (NumberFormatException ignored) {
            // Réponse identique pour éviter de révéler l'existence d'un matricule.
        }
        if (matricule != null) service.soumettre(matricule);
        return ResponseEntity.accepted().body(Map.of("message",
                "Si ce matricule correspond à un compte actif, la demande a été transmise à l'administrateur."));
    }

    @GetMapping("/api/admin/password-reset-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DemandeReinitialisationDTO> enAttente(Authentication authentication) {
        return service.enAttente((Integer) authentication.getPrincipal());
    }

    @PostMapping("/api/admin/password-reset-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approuver(@PathVariable Long id, Authentication authentication) {
        try {
            service.approuver(id, (Integer) authentication.getPrincipal());
            return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé et envoyé par e-mail."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/password-reset-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejeter(@PathVariable Long id, Authentication authentication) {
        try {
            service.rejeter(id, (Integer) authentication.getPrincipal());
            return ResponseEntity.ok(Map.of("message", "Demande rejetée."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }
}
