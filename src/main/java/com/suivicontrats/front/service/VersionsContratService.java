package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.VersionsContratRepository;
import org.springframework.security.access.AccessDeniedException;
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
    public VersionsContrat save(VersionsContrat v){ throw new AccessDeniedException("Les versions sont créées automatiquement lors de l’enregistrement d’un avenant."); }
    public VersionsContrat update(Long id, VersionsContrat details){
        getOne(id);
        throw new AccessDeniedException("Une version archivée est immuable.");
    }
    public void delete(Long id){ getOne(id); throw new AccessDeniedException("Une version archivée ne peut pas être supprimée."); }
}
