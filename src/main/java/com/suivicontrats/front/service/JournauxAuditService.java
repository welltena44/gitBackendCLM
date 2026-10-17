package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.JournauxAudit;
import com.suivicontrats.front.repository.JournauxAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JournauxAuditService {

    private final JournauxAuditRepository repo;

    public List<JournauxAudit> all(){ return repo.findAllByOrderByDateActionDesc(); }
    public JournauxAudit getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Entrée d'audit non trouvée"));
    }
    public List<JournauxAudit> byContrat(Integer contratId){ return repo.findByContratIdOrderByDateActionDesc(contratId); }
    public List<JournauxAudit> byUser(Integer userId){ return repo.findByUtilisateurIdOrderByDateActionDesc(userId); }
    public JournauxAudit save(JournauxAudit j){ j.setId(null); return repo.save(j); }
    public JournauxAudit update(Long id, JournauxAudit details){
        JournauxAudit journal = getOne(id);
        journal.setUtilisateurId(details.getUtilisateurId());
        journal.setContratId(details.getContratId());
        journal.setReferenceContrat(details.getReferenceContrat());
        journal.setAction(details.getAction());
        journal.setAncienneValeur(details.getAncienneValeur());
        journal.setNouvelleValeur(details.getNouvelleValeur());
        return repo.save(journal);
    }
    public void delete(Long id){ repo.delete(getOne(id)); }
}
