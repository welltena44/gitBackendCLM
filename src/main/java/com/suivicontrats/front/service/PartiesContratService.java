package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.PartiesContrat;
import com.suivicontrats.front.repository.PartiesContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartiesContratService {
    private final PartiesContratRepository repo;

    public List<PartiesContrat> getByContrat(Integer contratId){
        return repo.findByContratId(contratId);
    }

    public PartiesContrat getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Partie non trouvée"));
    }

    public PartiesContrat create(PartiesContrat p){
        p.setId(null);
        return repo.save(p);
    }

    public PartiesContrat update(Long id, PartiesContrat details){
        PartiesContrat p = getOne(id);
        p.setNom(details.getNom());
        p.setType(details.getType());
        p.setEmail(details.getEmail());
        p.setTelephone(details.getTelephone());
        return repo.save(p);
    }

    public void delete(Long id){ repo.deleteById(id); }
    public List<PartiesContrat> getAll(){ return repo.findAll(); }
}
