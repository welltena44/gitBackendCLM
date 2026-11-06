package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.service.ContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.suivicontrats.front.service.ContratPdfExtractionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
public class ContratController {
    private final ContratService service;
    private final ContratPdfExtractionService extractionService;

    @GetMapping
    public List<Contrat> all() {
        return service.getAll();
    }

    @GetMapping("/a-approuver")
    public List<Contrat> aApprouver() {
        return service.getPendingLegalApproval();
    }

    /** Contrats sans aucune règle de validation active : ils ne peuvent pas être soumis au circuit. */
    @GetMapping("/sans-regles")
    public List<Contrat> sansRegles() {
        return service.findSansRegles();
    }

    @GetMapping("/{id}")
    public Contrat one(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping(value = "/extraire-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE')")
    public ContratPdfExtractionService.Reponse extrairePdf(@RequestParam("file") MultipartFile file)
            throws IOException {
        return extractionService.extraire(file);
    }

    @PostMapping
    public Contrat create(@RequestBody Contrat contrat) {
        return service.create(contrat);
    }

    @PutMapping("/{id}")
    public Contrat update(@PathVariable Integer id, @RequestBody Contrat contrat) {
        return service.update(id, contrat);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }

    @PostMapping("/{id}/signer")
    public Contrat signer(@PathVariable Integer id) {
        return service.sign(id);
    }

    @PostMapping("/{id}/resilier")
    public Contrat resilier(@PathVariable Integer id) {
        return service.terminate(id);
    }
}
