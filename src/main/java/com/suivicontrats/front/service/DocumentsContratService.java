package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.DocumentsContrat;
import com.suivicontrats.front.repository.DocumentsContratRepository;
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

    @Value("${app.upload.dir:uploads/contrats}")
    private String uploadDir;

    public List<DocumentsContrat> getByContrat(Integer contratId){
        return repo.findByContratIdOrderByDateImportDesc(contratId);
    }

    public List<DocumentsContrat> getAll(){
        return repo.findAll();
    }

    public DocumentsContrat getById(Long id){
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Document non trouvé avec id " + id));
    }

    public DocumentsContrat update(Long id, DocumentsContrat details) {
        DocumentsContrat document = getById(id);
        document.setNomFichier(details.getNomFichier());
        document.setType(details.getType());
        document.setVersion(details.getVersion());
        document.setImportePar(details.getImportePar());
        return repo.save(document);
    }

    public DocumentsContrat upload(Integer contratId, MultipartFile file, Integer userId) throws IOException {
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
                .importePar(userId)
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
        Files.deleteIfExists(Paths.get(doc.getChemin()));
        repo.deleteById(id);
    }
}
