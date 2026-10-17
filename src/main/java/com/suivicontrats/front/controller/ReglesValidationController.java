package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.service.ReglesValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/regles-validation")
@RequiredArgsConstructor
public class ReglesValidationController {

    private final ReglesValidationService service;

    @GetMapping
    public List<ReglesValidation> all(){ return service.getAll(); }

    @GetMapping("/{id}")
    public ReglesValidation one(@PathVariable Long id){ return service.getOne(id); }

    @GetMapping("/type/{typeId}")
    public List<ReglesValidation> byType(@PathVariable Long typeId){ return service.getByType(typeId); }

    // LE PLUS IMPORTANT : savoir qui doit valider un contrat de X MGA
    // GET /api/regles-validation/circuit?typeContratId=1&montant=25000
    @GetMapping("/circuit")
    public List<ReglesValidation> circuit(
            @RequestParam Long typeContratId,
            @RequestParam BigDecimal montant
    ){
        return service.getCircuit(typeContratId, montant);
    }

    @PostMapping
    public ReglesValidation create(@RequestBody ReglesValidation r){ return service.create(r); }

    @PutMapping("/{id}")
    public ReglesValidation update(@PathVariable Long id, @RequestBody ReglesValidation r){ return service.update(id, r); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
