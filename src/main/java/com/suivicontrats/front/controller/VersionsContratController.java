package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.service.VersionsContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/versions-contrat")
@RequiredArgsConstructor
public class VersionsContratController {

    private final VersionsContratService service;

    @GetMapping
    public List<VersionsContrat> all(){ return service.all(); }

    @GetMapping("/contrat/{contratId}")
    public List<VersionsContrat> byContrat(@PathVariable Integer contratId){
        return service.byContrat(contratId);
    }

    @GetMapping("/{id}")
    public VersionsContrat one(@PathVariable Long id){ return service.getOne(id); }

    @PostMapping
    public VersionsContrat create(@RequestBody VersionsContrat v){ return service.save(v); }

    @PutMapping("/{id}")
    public VersionsContrat update(@PathVariable Long id, @RequestBody VersionsContrat v){
        return service.update(id, v);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
