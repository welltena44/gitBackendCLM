package com.suivicontrats.front.controller;

import com.suivicontrats.front.dto.LoginRequest;
import com.suivicontrats.front.dto.LoginResponse;
import com.suivicontrats.front.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.Map;
import com.suivicontrats.front.service.UtilisateursService;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UtilisateursService utilisateursService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body){
        try{ return ResponseEntity.ok(authService.login(body)); }
        catch(RuntimeException e){ return ResponseEntity.status(401).body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/demande-acces")
    public Map<String,String> demande(@RequestBody Map<String,String> b){
        return Map.of("message","Demande reçue pour "+b.get("email"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            Integer authenticatedUserId = (Integer) authentication.getPrincipal();
            if (!authenticatedUserId.toString().equals(body.get("userId"))) {
                return ResponseEntity.status(403).body(Map.of("message", "Vous ne pouvez modifier que votre propre mot de passe."));
            }
            utilisateursService.changerMotDePasse(
                authenticatedUserId, body.get("motDePasseActuel"), body.get("nouveauMotDePasse"));
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
