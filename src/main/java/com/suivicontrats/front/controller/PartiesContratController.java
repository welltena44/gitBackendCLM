package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.PartiesContrat;
import com.suivicontrats.front.service.PartiesContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PartiesContratController {

    private final PartiesContratService service;

    // Liste toutes les parties
    @GetMapping("/parties-contrat")
    public List<PartiesContrat> all() { return service.getAll(); }

    @GetMapping("/parties-contrat/{id}")
    public PartiesContrat one(@PathVariable Long id){ return service.getOne(id); }

    // Parties d'un contrat précis -> GET /api/contrats/1/parties
    @GetMapping("/contrats/{contratId}/parties")
    public List<PartiesContrat> byContrat(@PathVariable Integer contratId){
        return service.getByContrat(contratId);
    }

    @PostMapping("/parties-contrat")
    public PartiesContrat create(@RequestBody PartiesContrat p){
        return service.create(p);
    }

    @PutMapping("/parties-contrat/{id}")
    public PartiesContrat update(@PathVariable Long id, @RequestBody PartiesContrat p){
        return service.update(id, p);
    }

    @DeleteMapping("/parties-contrat/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
