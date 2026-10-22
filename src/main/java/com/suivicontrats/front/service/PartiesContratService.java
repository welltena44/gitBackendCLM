package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.PartiesContrat;
import com.suivicontrats.front.repository.PartiesContratRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartiesContratService {
    private final PartiesContratRepository repo;
    private final ContratService contratService;
    private final JournauxAuditService audit;

    public List<PartiesContrat> getByContrat(Integer contratId){
        contratService.getById(contratId);
        return repo.findByContratId(contratId);
    }

    public PartiesContrat getOne(Long id){
        PartiesContrat p = repo.findById(id).orElseThrow(() -> new RuntimeException("Partie non trouvée"));
        contratService.getById(p.getContratId());
        return p;
    }

    public PartiesContrat create(PartiesContrat p){
        if (!canManage()) throw new AccessDeniedException("Modification réservée au gestionnaire du contrat.");
        var contract = contratService.getById(p.getContratId());
        p.setId(null);
        PartiesContrat saved = repo.save(p);
        audit.record(currentUserId(), contract.getId(), contract.getReference(), "PARTIE_AJOUTEE", null, "nom=" + saved.getNom() + "; type=" + saved.getType());
        return saved;
    }

    public PartiesContrat update(Long id, PartiesContrat details){
        PartiesContrat p = getOne(id);
        if (!canManage()) throw new AccessDeniedException("Modification réservée au gestionnaire du contrat.");
        String old = "nom=" + p.getNom() + "; type=" + p.getType();
        p.setNom(details.getNom());
        p.setType(details.getType());
        p.setEmail(details.getEmail());
        p.setTelephone(details.getTelephone());
        PartiesContrat saved = repo.save(p);
        var contract = contratService.getById(saved.getContratId());
        audit.record(currentUserId(), contract.getId(), contract.getReference(), "PARTIE_MODIFIEE", old, "nom=" + saved.getNom() + "; type=" + saved.getType());
        return saved;
    }

    public void delete(Long id){ PartiesContrat p = getOne(id); if (!canManage()) throw new AccessDeniedException("Suppression réservée au gestionnaire du contrat."); var contract = contratService.getById(p.getContratId()); audit.record(currentUserId(), contract.getId(), contract.getReference(), "PARTIE_SUPPRIMEE", "nom=" + p.getNom() + "; type=" + p.getType(), null); repo.deleteById(id); }
    public List<PartiesContrat> getAll(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(p -> allowed.contains(p.getContratId())).toList();
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
