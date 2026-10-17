package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ReglesValidation;
import com.suivicontrats.front.repository.ReglesValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReglesValidationService {

    private final ReglesValidationRepository repo;

    public List<ReglesValidation> getAll(){ return repo.findAllByOrderByNiveauAsc(); }

    public ReglesValidation getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Règle de validation non trouvée"));
    }
    
    public List<ReglesValidation> getByType(Long typeId){ return repo.findByTypeContratIdOrderByNiveauAsc(typeId); }

    public ReglesValidation create(ReglesValidation r){
        // Validation chk_montants
        if(r.getMontantMaximum() != null && r.getMontantMaximum().compareTo(r.getMontantMinimum()) <= 0){
            throw new RuntimeException("montant_maximum doit être > montant_minimum");
        }
        r.setId(null);
        return repo.save(r);
    }

    public ReglesValidation update(Long id, ReglesValidation r){
        ReglesValidation existing = getOne(id);
        existing.setTypeContratId(r.getTypeContratId());
        existing.setMontantMinimum(r.getMontantMinimum());
        existing.setMontantMaximum(r.getMontantMaximum());
        existing.setNiveau(r.getNiveau());
        existing.setRoleRequis(r.getRoleRequis());
        return repo.save(existing);
    }

    public void delete(Long id){ repo.deleteById(id); }

    // C'est ce que tu vas appeler pour un contrat
    public List<ReglesValidation> getCircuit(Long typeContratId, BigDecimal montant){
        return repo.findCircuitApplicable(typeContratId, montant);
    }
}
