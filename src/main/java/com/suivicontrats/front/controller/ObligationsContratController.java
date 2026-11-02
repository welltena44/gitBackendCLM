package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.ObligationsContrat;
import com.suivicontrats.front.service.ObligationsContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ObligationsContratController {
    private final ObligationsContratService service;

    @GetMapping("/contrats/{contractId}/obligations")
    public List<ObligationsContrat> byContract(@PathVariable Integer contractId) {
        return service.byContract(contractId);
    }

    @PostMapping("/contrats/{contractId}/obligations")
    public ObligationsContrat create(@PathVariable Integer contractId, @RequestBody ObligationsContrat obligation) {
        return service.create(contractId, obligation);
    }

    @PutMapping("/obligations/{id}/cloturer")
    public ObligationsContrat close(@PathVariable Long id) {
        return service.close(id);
    }

    @PutMapping("/obligations/{id}/annuler")
    public ObligationsContrat cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
