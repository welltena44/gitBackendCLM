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
        contratService.getById(p.getContratId());
        p.setId(null);
        return repo.save(p);
    }

    public PartiesContrat update(Long id, PartiesContrat details){
        PartiesContrat p = getOne(id);
        if (!canManage()) throw new AccessDeniedException("Modification réservée au gestionnaire du contrat.");
        p.setNom(details.getNom());
        p.setType(details.getType());
        p.setEmail(details.getEmail());
        p.setTelephone(details.getTelephone());
        return repo.save(p);
    }

    public void delete(Long id){ getOne(id); if (!canManage()) throw new AccessDeniedException("Suppression réservée au gestionnaire du contrat."); repo.deleteById(id); }
    public List<PartiesContrat> getAll(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(p -> allowed.contains(p.getContratId())).toList();
    }
    private boolean canManage(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_GESTIONNAIRE"));
    }
}
