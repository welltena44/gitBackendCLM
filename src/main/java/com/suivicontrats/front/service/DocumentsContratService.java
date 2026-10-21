package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.DocumentsContrat;
import com.suivicontrats.front.repository.DocumentsContratRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentsContratService {

    private final DocumentsContratRepository repo;
    private final ContratService contratService;

    @Value("${app.upload.dir:uploads/contrats}")
    private String uploadDir;

    public List<DocumentsContrat> getByContrat(Integer contratId){
        contratService.getById(contratId);
        return repo.findByContratIdOrderByDateImportDesc(contratId);
    }

    public List<DocumentsContrat> getAll(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(d -> allowed.contains(d.getContratId())).toList();
    }

    public DocumentsContrat getById(Long id){
        DocumentsContrat doc = repo.findById(id).orElseThrow(() -> new RuntimeException("Document non trouvé avec id " + id));
        contratService.getById(doc.getContratId());
        return doc;
    }

    public DocumentsContrat update(Long id, DocumentsContrat details) {
        DocumentsContrat document = getById(id);
        if (!canManage()) throw new AccessDeniedException("Modification réservée au gestionnaire du contrat.");
        document.setNomFichier(details.getNomFichier());
        document.setType(details.getType());
        document.setVersion(details.getVersion());
        return repo.save(document);
    }

    public DocumentsContrat upload(Integer contratId, MultipartFile file, Integer userId) throws IOException {
        if (!canManage()) throw new AccessDeniedException("Ajout réservé au gestionnaire du contrat.");
        contratService.getById(contratId);
        if(file.isEmpty()){
            throw new RuntimeException("Fichier vide");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID() + ext;

        Path contratPath = Paths.get(uploadDir, contratId.toString());
        Files.createDirectories(contratPath);

        Path filePath = contratPath.resolve(storedName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        int nextVersion = repo.findByContratId(contratId).size() + 1;

        DocumentsContrat doc = DocumentsContrat.builder()
                .contratId(contratId)
                .nomFichier(originalName)
                .chemin(filePath.toString())
                .type(file.getContentType())
                .version(nextVersion)
                .importePar((Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .dateImport(LocalDateTime.now())
                .build();

        return repo.save(doc);
    }

    public Resource download(Long id) throws MalformedURLException {
        DocumentsContrat doc = getById(id);
        Path path = Paths.get(doc.getChemin());
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            throw new RuntimeException("Fichier introuvable sur disque: " + doc.getChemin());
        }
        return resource;
    }

    public void delete(Long id) throws IOException {
        DocumentsContrat doc = getById(id);
        if (!canManage()) throw new AccessDeniedException("Suppression réservée au gestionnaire du contrat.");
        Files.deleteIfExists(Paths.get(doc.getChemin()));
        repo.deleteById(id);
    }

    private boolean canManage(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_GESTIONNAIRE"));
    }
}
