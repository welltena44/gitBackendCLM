package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.TypesContrat;
import com.suivicontrats.front.repository.TypesContratRepository;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.ReglesValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TypesContratService {
    private final TypesContratRepository repo;
    private final ContratRepository contrats;
    private final ReglesValidationRepository regles;
    private final JournauxAuditService audit;

    public List<TypesContrat> all() { return repo.findAllByOrderByNomAsc(); }
    public List<TypesContrat> actifs() { return repo.findByActifTrue(); }
    public TypesContrat getOne(Long id) { return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Type de contrat introuvable.")); }

    @Transactional
    public TypesContrat create(TypesContrat t) {
        valider(t); t.setId(null);
        if (repo.existsByNomIgnoreCase(t.getNom().trim())) throw new IllegalArgumentException("Un type de contrat portant ce nom existe déjà.");
        t.setNom(t.getNom().trim());
        TypesContrat saved = repo.save(t);
        auditAdmin("TYPE_CONTRAT_CREE", null, "id=" + saved.getId() + "; nom=" + saved.getNom());
        return saved;
    }

    @Transactional
    public TypesContrat update(Long id, TypesContrat details) {
        TypesContrat type = getOne(id); valider(details);
        String old = "nom=" + type.getNom() + "; actif=" + type.getActif();
        if (repo.findAll().stream().anyMatch(x -> !x.getId().equals(id) && x.getNom().equalsIgnoreCase(details.getNom().trim())))
            throw new IllegalArgumentException("Un autre type de contrat porte déjà ce nom.");
        type.setNom(details.getNom().trim()); type.setDescription(details.getDescription()); type.setActif(details.getActif());
        TypesContrat saved = repo.save(type);
        auditAdmin("TYPE_CONTRAT_MODIFIE", old, "nom=" + saved.getNom() + "; actif=" + saved.getActif());
        return saved;
    }

    /** Suppression logique : un type référencé n'est jamais supprimé physiquement. */
    @Transactional
    public void delete(Long id) {
        TypesContrat type = getOne(id);
        boolean utilise = contrats.existsByTypeId(id) || regles.existsByTypeContratId(id);
        if (utilise) {
            type.setActif(false); repo.save(type);
            auditAdmin("TYPE_CONTRAT_DESACTIVE", "actif=" + type.getActif(), "id=" + id);
            return;
        }
        repo.delete(type);
        auditAdmin("TYPE_CONTRAT_SUPPRIME", "id=" + id + "; nom=" + type.getNom(), null);
    }

    private void valider(TypesContrat t) {
        if (t.getNom() == null || t.getNom().isBlank()) throw new IllegalArgumentException("Le nom du type de contrat est obligatoire.");
        if (t.getNom().trim().length() > 100) throw new IllegalArgumentException("Le nom ne doit pas dépasser 100 caractères.");
    }
    private void auditAdmin(String action, String oldValue, String newValue) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Integer id = principal instanceof Integer ? (Integer) principal : null;
        audit.record(id, null, null, action, oldValue, newValue);
    }
}
