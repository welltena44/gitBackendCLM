package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.JournauxAudit;
import com.suivicontrats.front.service.JournauxAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/journaux-audit")
@RequiredArgsConstructor
public class JournauxAuditController {

    private final JournauxAuditService service;

    @GetMapping
    public List<JournauxAudit> all(){ return service.all(); }

    @GetMapping("/{id}")
    public JournauxAudit one(@PathVariable Long id){ return service.getOne(id); }

    @GetMapping("/contrat/{contratId}")
    public List<JournauxAudit> byContrat(@PathVariable Integer contratId){
        return service.byContrat(contratId);
    }

    @GetMapping("/user/{userId}")
    public List<JournauxAudit> byUser(@PathVariable Integer userId){
        return service.byUser(userId);
    }

    @PostMapping
    public JournauxAudit create(@RequestBody JournauxAudit j){ return service.save(j); }

    @PutMapping("/{id}")
    public JournauxAudit update(@PathVariable Long id, @RequestBody JournauxAudit j){
        return service.update(id, j);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){ service.delete(id); }
}
