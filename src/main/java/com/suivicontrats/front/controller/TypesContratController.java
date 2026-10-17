package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.TypesContrat;
import com.suivicontrats.front.service.TypesContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/types-contrat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // Ajustez le port si votre React tourne sur un autre port (ex: 5173)
public class TypesContratController {

    private final TypesContratService service;

    // Récupérer tous les types de contrats (triés par nom)
    @GetMapping
    public List<TypesContrat> all() {
        return service.all();
    }

    // Récupérer uniquement les types actifs
    @GetMapping("/actifs")
    public List<TypesContrat> actifs() {
        return service.actifs();
    }

    // Récupérer un type de contrat par son ID
    @GetMapping("/{id}")
    public TypesContrat one(@PathVariable Long id) {
        return service.getOne(id);
    }

    // Créer ou Mettre à jour un type de contrat
    @PostMapping
    public TypesContrat save(@RequestBody TypesContrat t) {
        return service.create(t);
    }

    // Mettre à jour explicitement (si vous utilisez PUT depuis React)
    @PutMapping("/{id}")
    public TypesContrat update(@PathVariable Long id, @RequestBody TypesContrat t) {
        return service.update(id, t);
    }

    // Supprimer un type de contrat
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
