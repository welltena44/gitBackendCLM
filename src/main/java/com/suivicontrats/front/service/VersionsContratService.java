package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.VersionsContratRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionsContratService {

    private final VersionsContratRepository repo;
    private final ContratService contratService;

    public List<VersionsContrat> all(){
        var allowed = contratService.getAll().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        return repo.findAll().stream().filter(v -> allowed.contains(v.getContratId())).toList();
    }
    public List<VersionsContrat> byContrat(Integer contratId){ contratService.getById(contratId); return repo.findByContratIdOrderByVersionAsc(contratId); }
    public VersionsContrat getOne(Long id){ VersionsContrat v = repo.findById(id).orElseThrow(() -> new RuntimeException("Version non trouvée")); contratService.getById(v.getContratId()); return v; }
    public VersionsContrat save(VersionsContrat v){ requireAdmin(); contratService.getById(v.getContratId()); v.setId(null); return repo.save(v); }
    public VersionsContrat update(Long id, VersionsContrat details){
        requireAdmin();
        VersionsContrat version = getOne(id);
        contratService.getById(details.getContratId());
        version.setContratId(details.getContratId());
        version.setVersion(details.getVersion());
        version.setContenu(details.getContenu());
        version.setCreePar(details.getCreePar());
        version.setAvenantId(details.getAvenantId());
        return repo.save(version);
    }
    public void delete(Long id){ requireAdmin(); getOne(id); repo.deleteById(id); }
    private void requireAdmin(){ Authentication auth=SecurityContextHolder.getContext().getAuthentication(); if(auth==null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN"))) throw new AccessDeniedException("Opération réservée à l'administrateur."); }
}
