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
    private final JournauxAuditService audit;

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
        if ("CONTRAT_SIGNE".equals(document.getCategorie()))
            throw new IllegalArgumentException("Les métadonnées du contrat signé sont conservées dans l'archive.");
        String old = "nom=" + document.getNomFichier() + "; type=" + document.getType();
        document.setNomFichier(details.getNomFichier());
        document.setType(details.getType());
        document.setVersion(details.getVersion());
        DocumentsContrat saved = repo.save(document);
        audit.record(currentUserId(), saved.getContratId(), null, "DOCUMENT_MODIFIE", old, "nom=" + saved.getNomFichier() + "; type=" + saved.getType());
        return saved;
    }

    public DocumentsContrat upload(Integer contratId, MultipartFile file, Integer userId, String categorie) throws IOException {
        if (!canManage()) throw new AccessDeniedException("Ajout réservé au gestionnaire du contrat.");
        var contrat = contratService.getById(contratId);
        categorie = categorie == null ? "JUSTIFICATIF" : categorie.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("JUSTIFICATIF", "CONTRAT_SIGNE").contains(categorie))
            throw new IllegalArgumentException("Catégorie de document invalide.");
        if ("CONTRAT_SIGNE".equals(categorie)) {
            if (!"APPROUVE".equals(contrat.getStatut()))
                throw new IllegalArgumentException("Le contrat signé ne peut être déposé qu'après l'approbation interne.");
            if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                    || file.getSize() < 5 || !new String(file.getInputStream().readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new IllegalArgumentException("Le contrat signé doit être un fichier PDF valide.");
        }
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
                .categorie(categorie)
                .version(nextVersion)
                .importePar((Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .dateImport(LocalDateTime.now())
                .build();

        DocumentsContrat saved = repo.save(doc);
        audit.record(currentUserId(), contratId, null, "DOCUMENT_AJOUTE", null, "nom=" + originalName + "; catégorie=" + categorie + "; type=" + file.getContentType());
        return saved;
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
        if ("CONTRAT_SIGNE".equals(doc.getCategorie()))
            throw new IllegalArgumentException("Le document du contrat signé est conservé dans l'archive.");
        Integer actor = currentUserId();
        audit.record(actor, doc.getContratId(), null, "DOCUMENT_SUPPRIME", "nom=" + doc.getNomFichier(), null);
        Files.deleteIfExists(Paths.get(doc.getChemin()));
        repo.deleteById(id);
    }

    private boolean canManage(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_GESTIONNAIRE"));
    }
    private Integer currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof Integer id ? id : null;
    }
}
