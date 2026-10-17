package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Avenants;
import com.suivicontrats.front.repository.AvenantsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvenantsService {

    private final AvenantsRepository repo;

    public List<Avenants> all(){ return repo.findAll(); }
    public List<Avenants> byContrat(Integer contratId){ return repo.findByContratIdOrderByNumeroAsc(contratId); }
    public Avenants getOne(Long id){ return repo.findById(id).orElseThrow(() -> new RuntimeException("Avenant non trouvé")); }
    public Avenants save(Avenants a){ a.setId(null); return repo.save(a); }
    public Avenants update(Long id, Avenants details){
        Avenants a = getOne(id);
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
        a.setCreePar(details.getCreePar());
        return repo.save(a);
    }
    public void delete(Long id){ repo.deleteById(id); }
}
