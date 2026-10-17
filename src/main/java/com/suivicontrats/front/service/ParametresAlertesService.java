package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParametresAlertesService {
    private final ParametresAlertesRepository repo;

    public List<ParametresAlertes> all(){ return repo.findAllByOrderByJoursAvantAsc(); }
    public List<ParametresAlertes> actifs(){ return repo.findByActifTrueOrderByJoursAvantAsc(); }
    public ParametresAlertes getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Paramètre d'alerte non trouvé"));
    }
    public ParametresAlertes create(ParametresAlertes p){ p.setId(null); return repo.save(p); }
    public ParametresAlertes update(Long id, ParametresAlertes details){
        ParametresAlertes parametre = getOne(id);
        parametre.setJoursAvant(details.getJoursAvant());
        parametre.setActif(details.getActif());
        return repo.save(parametre);
    }
    public void delete(Long id){ repo.deleteById(id); }
}
