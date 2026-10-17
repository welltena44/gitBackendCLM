package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.TypesContrat;
import com.suivicontrats.front.repository.TypesContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TypesContratService {

    private final TypesContratRepository repo;

    public List<TypesContrat> all(){ return repo.findAllByOrderByNomAsc(); }
    public List<TypesContrat> actifs(){ return repo.findByActifTrue(); }
    public TypesContrat getOne(Long id){ return repo.findById(id).orElseThrow(() -> new RuntimeException("Type contrat non trouvé")); }
    public TypesContrat create(TypesContrat t){ t.setId(null); return repo.save(t); }
    public TypesContrat update(Long id, TypesContrat details){
        TypesContrat type = getOne(id);
        type.setNom(details.getNom());
        type.setDescription(details.getDescription());
        type.setActif(details.getActif());
        return repo.save(type);
    }
    public void delete(Long id){ repo.deleteById(id); }
}
