
package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.dto.UtilisateursDTO;
import com.suivicontrats.front.dto.UserDirectoryDTO;
import jakarta.validation.Valid;
import com.suivicontrats.front.service.UtilisateursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateursController {

    private final UtilisateursService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE','VALIDEUR','JURIDIQUE')")
    public ResponseEntity<List<?>> all(Authentication authentication) {
        List<Utilisateurs> users = service.all();
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) return ResponseEntity.<List<?>>ok(users);
        List<UserDirectoryDTO> directory = users.stream().map(u -> new UserDirectoryDTO(u.getId(), u.getNom(), u.getPrenom(), u.getService())).toList();
        return ResponseEntity.<List<?>>ok(directory);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal == #id")
    public ResponseEntity<Utilisateurs> one(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getOne(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Utilisateurs> create(
            @RequestBody Utilisateurs utilisateur) {

        return ResponseEntity.ok(service.create(utilisateur));
    }

    @PutMapping("/{id}/profil")
    @PreAuthorize("hasRole('ADMIN') or principal == #id")
    public ResponseEntity<UtilisateursDTO> updateProfile(
            @PathVariable Integer id,
            @Valid @RequestBody ProfilRequest request) {
        return ResponseEntity.ok(service.updateProfile(id, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Utilisateurs> update(
            @PathVariable Integer id,
            @RequestBody Utilisateurs utilisateur) {

        return ResponseEntity.ok(service.update(id, utilisateur));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
