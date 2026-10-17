package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.service.AvenantsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/avenants")
@RequiredArgsConstructor
public class AvenantsController {

    private final AvenantsService service;

    @GetMapping
    public List<Avenants> all(){ return service.all(); }

    @GetMapping("/contrat/{contratId}")
    public List<Avenants> byContrat(@PathVariable Integer contratId){
        return service.byContrat(contratId);
    }

    @GetMapping("/{id}")
    public Avenants one(@PathVariable Long id){ return service.getOne(id); }

    @PostMapping
    public Avenants create(@RequestBody Avenants a){ return service.save(a); }

    @PutMapping("/{id}")
    public Avenants update(@PathVariable Long id, @RequestBody Avenants a){
        return service.update(id, a);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
