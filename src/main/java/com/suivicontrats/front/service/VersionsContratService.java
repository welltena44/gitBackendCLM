package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.VersionsContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionsContratService {

    private final VersionsContratRepository repo;

    public List<VersionsContrat> all(){ return repo.findAll(); }
    public List<VersionsContrat> byContrat(Integer contratId){ return repo.findByContratIdOrderByVersionAsc(contratId); }
    public VersionsContrat getOne(Long id){ return repo.findById(id).orElseThrow(() -> new RuntimeException("Version non trouvée")); }
    public VersionsContrat save(VersionsContrat v){ v.setId(null); return repo.save(v); }
    public VersionsContrat update(Long id, VersionsContrat details){
        VersionsContrat version = getOne(id);
        version.setContratId(details.getContratId());
        version.setVersion(details.getVersion());
        version.setContenu(details.getContenu());
        version.setCreePar(details.getCreePar());
        version.setAvenantId(details.getAvenantId());
        return repo.save(version);
    }
    public void delete(Long id){ repo.deleteById(id); }
}
