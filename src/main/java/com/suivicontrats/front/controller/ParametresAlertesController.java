package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.service.ParametresAlertesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/parametres-alertes")
@RequiredArgsConstructor
public class ParametresAlertesController {

    private final ParametresAlertesService service;

    @GetMapping
    public List<ParametresAlertes> all(){ return service.all(); }

    @GetMapping("/{id}")
    public ParametresAlertes one(@PathVariable Long id){ return service.getOne(id); }

    @GetMapping("/actifs")
    public List<ParametresAlertes> actifs(){ return service.actifs(); }

    @PostMapping
    public ParametresAlertes create(@RequestBody ParametresAlertes p){ return service.create(p); }

    @PutMapping("/{id}")
    public ParametresAlertes update(@PathVariable Long id, @RequestBody ParametresAlertes p){
        return service.update(id, p);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
