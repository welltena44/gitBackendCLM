package com.suivicontrats.front.controller;

import com.suivicontrats.front.entity.DocumentsContrat;
import com.suivicontrats.front.service.DocumentsContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentsContratController {

    private final DocumentsContratService service;

    @GetMapping("/documents")
    public List<DocumentsContrat> all(){
        return service.getAll();
    }

    @GetMapping("/contrats/{contratId}/documents")
    public List<DocumentsContrat> byContrat(@PathVariable Integer contratId){
        return service.getByContrat(contratId);
    }

    @PostMapping(value = "/contrats/{contratId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentsContrat upload(
            @PathVariable Integer contratId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Integer userId
    ) throws IOException {
        return service.upload(contratId, file, userId);
    }

    @GetMapping("/documents/{id}")
    public DocumentsContrat getInfo(@PathVariable Long id){
        return service.getById(id);
    }

    @PutMapping("/documents/{id}")
    public DocumentsContrat update(@PathVariable Long id, @RequestBody DocumentsContrat document){
        return service.update(id, document);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        DocumentsContrat doc = service.getById(id);
        Resource resource = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNomFichier() + "\"")
                .body(resource);
    }

    @DeleteMapping("/documents/{id}")
    public void delete(@PathVariable Long id) throws IOException {
        service.delete(id);
    }
}
