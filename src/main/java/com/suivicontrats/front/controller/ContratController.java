package com.suivicontrats.front.controller;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.service.ContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
public class ContratController {
    private final ContratService service;

    @GetMapping
    public List<Contrat> all() { return service.getAll(); }

    @GetMapping("/{id}")
    public Contrat one(@PathVariable Integer id) { return service.getById(id); }

    @PostMapping
    public Contrat create(@RequestBody Contrat contrat) { return service.create(contrat); }

    @PutMapping("/{id}")
    public Contrat update(@PathVariable Integer id, @RequestBody Contrat contrat) { return service.update(id, contrat); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) { service.delete(id); }

    @PostMapping("/{id}/signer")
    public Contrat signer(@PathVariable Integer id) { return service.sign(id); }

    @PostMapping("/{id}/resilier")
    public Contrat resilier(@PathVariable Integer id) { return service.terminate(id); }
}
