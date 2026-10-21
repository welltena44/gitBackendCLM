package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.service.VersionsContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/versions-contrat")
@RequiredArgsConstructor
public class VersionsContratController {

    private final VersionsContratService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','JURIDIQUE')")
    public List<VersionsContrat> all(){ return service.all(); }

    @GetMapping("/contrat/{contratId}")
    public List<VersionsContrat> byContrat(@PathVariable Integer contratId){
        return service.byContrat(contratId);
    }

    @GetMapping("/{id}")
    public VersionsContrat one(@PathVariable Long id){ return service.getOne(id); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public VersionsContrat create(@RequestBody VersionsContrat v){ return service.save(v); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VersionsContrat update(@PathVariable Long id, @RequestBody VersionsContrat v){
        return service.update(id, v);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
