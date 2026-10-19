
package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.dto.UtilisateursDTO;
import jakarta.validation.Valid;
import com.suivicontrats.front.service.UtilisateursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateursController {

    private final UtilisateursService service;

    @GetMapping
    public ResponseEntity<List<Utilisateurs>> all() {
        return ResponseEntity.ok(service.all());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Utilisateurs> one(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getOne(id));
    }

    @PostMapping
    public ResponseEntity<Utilisateurs> create(
            @RequestBody Utilisateurs utilisateur) {

        return ResponseEntity.ok(service.create(utilisateur));
    }

    @PutMapping("/{id}/profil")
    public ResponseEntity<UtilisateursDTO> updateProfile(
            @PathVariable Integer id,
            @Valid @RequestBody ProfilRequest request) {
        return ResponseEntity.ok(service.updateProfile(id, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Utilisateurs> update(
            @PathVariable Integer id,
            @RequestBody Utilisateurs utilisateur) {

        return ResponseEntity.ok(service.update(id, utilisateur));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
