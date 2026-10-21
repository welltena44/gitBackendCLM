package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.repository.AvenantsRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvenantsService {

    private final AvenantsRepository repo;
    private final ContratService contratService;

    public List<Avenants> all(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(a -> allowed.contains(a.getContratId())).toList();
    }
    public List<Avenants> byContrat(Integer contratId){ contratService.getById(contratId); return repo.findByContratIdOrderByNumeroAsc(contratId); }
    public Avenants getOne(Long id){ Avenants a = repo.findById(id).orElseThrow(() -> new RuntimeException("Avenant non trouvé")); contratService.getById(a.getContratId()); return a; }
    public Avenants save(Avenants a){
        if (!canManage()) throw new AccessDeniedException("Seul le gestionnaire du contrat peut créer un avenant.");
        contratService.getById(a.getContratId()); a.setId(null);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        a.setCreePar((Integer) auth.getPrincipal());
        return repo.save(a);
    }
    public Avenants update(Long id, Avenants details){
        Avenants a = getOne(id);
        if (!canManage()) throw new AccessDeniedException("Seul le gestionnaire du contrat peut modifier un avenant.");
        contratService.getById(details.getContratId());
        a.setContratId(details.getContratId());
        a.setNumero(details.getNumero());
        a.setDescription(details.getDescription());
        a.setModifications(details.getModifications());
        a.setDateAvenant(details.getDateAvenant());
        a.setMontantAvant(details.getMontantAvant());
        a.setMontantApres(details.getMontantApres());
        a.setDateFinAvant(details.getDateFinAvant());
        a.setDateFinApres(details.getDateFinApres());
        a.setDocumentId(details.getDocumentId());
        return repo.save(a);
    }
    public void delete(Long id){ getOne(id); if (!canManage()) throw new AccessDeniedException("Seul le gestionnaire du contrat peut supprimer un avenant."); repo.deleteById(id); }

    private boolean canManage(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_GESTIONNAIRE"));
    }
}
