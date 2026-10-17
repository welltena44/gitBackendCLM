package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.service.ValidationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping
    public Validations create(@RequestBody Validations validation){ return service.create(validation); }

    @PutMapping("/{id}")
    public Validations update(@PathVariable Long id, @RequestBody Validations validation){
        return service.update(id, validation);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }

    @GetMapping("/contrat/{contratId}")
    public List<Validations> byContrat(@PathVariable Integer contratId){
        return service.getByContrat(contratId);
    }

    @PostMapping("/contrat/{contratId}/generer")
    public List<Validations> generer(@PathVariable Integer contratId, @RequestParam Integer demandeurId){
        return service.genererCircuit(contratId, demandeurId);
    }

    @PostMapping("/{id}/decider")
    public Validations decider(@PathVariable Long id, @RequestBody Map<String,Object> body){
        Integer valideurId = Integer.valueOf(body.get("valideurId").toString());
        String decision = body.get("decision").toString();
        String commentaire = (String) body.getOrDefault("commentaire", "");
        return service.decider(id, valideurId, decision, commentaire);
    }
}
