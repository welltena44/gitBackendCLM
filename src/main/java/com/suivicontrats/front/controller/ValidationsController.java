package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.service.ValidationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/validations")
@RequiredArgsConstructor
public class ValidationsController {

    private final ValidationsService service;

    @GetMapping
    public List<Validations> all(){ return service.all(); }

    @GetMapping("/{id}")
    public Validations one(@PathVariable Long id){ return service.getOne(id); }

    @GetMapping("/contrat/{contratId}")
    public List<Validations> byContrat(@PathVariable Integer contratId){
        return service.getByContrat(contratId);
    }

    @PostMapping("/contrat/{contratId}/generer")
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public List<Validations> generer(@PathVariable Integer contratId, @RequestParam(required = false) Integer demandeurId){
        return service.genererCircuit(contratId, demandeurId);
    }

    @PostMapping("/{id}/decider")
    @PreAuthorize("hasRole('VALIDEUR')")
    public Validations decider(@PathVariable Long id, @RequestBody Map<String,Object> body, Authentication authentication){
        Integer valideurId = (Integer) authentication.getPrincipal();
        String decision = body.get("decision").toString();
        String commentaire = (String) body.getOrDefault("commentaire", "");
        return service.decider(id, valideurId, decision, commentaire);
    }
}
