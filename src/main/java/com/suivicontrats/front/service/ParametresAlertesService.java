package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.ParametresAlertes;
import com.suivicontrats.front.repository.ParametresAlertesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParametresAlertesService {
    private final ParametresAlertesRepository repo;
    private final JournauxAuditService audit;
    public List<ParametresAlertes> all() { return repo.findAllByOrderByJoursAvantAsc(); }
    public List<ParametresAlertes> actifs() { return repo.findByActifTrueOrderByJoursAvantAsc(); }
    public ParametresAlertes getOne(Long id) { return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Paramètre d'alerte introuvable.")); }
    @Transactional public ParametresAlertes create(ParametresAlertes p) {
        valider(p.getJoursAvant(), null); p.setId(null); ParametresAlertes saved=repo.save(p);
        auditAdmin("PARAMETRE_ALERTE_CREE", null, "joursAvant="+saved.getJoursAvant()+"; actif="+saved.getActif()); return saved;
    }
    @Transactional public ParametresAlertes update(Long id, ParametresAlertes d) {
        ParametresAlertes p=getOne(id); valider(d.getJoursAvant(), id); String old="joursAvant="+p.getJoursAvant()+"; actif="+p.getActif();
        p.setJoursAvant(d.getJoursAvant()); p.setActif(d.getActif()); ParametresAlertes saved=repo.save(p);
        auditAdmin("PARAMETRE_ALERTE_MODIFIE", old, "joursAvant="+saved.getJoursAvant()+"; actif="+saved.getActif()); return saved;
    }
    @Transactional public void delete(Long id) { ParametresAlertes p=getOne(id); repo.delete(p); auditAdmin("PARAMETRE_ALERTE_SUPPRIME", "joursAvant="+p.getJoursAvant(), null); }
    private void valider(Integer jours, Long exclude) {
        if (jours == null || jours < 1 || jours > 3650) throw new IllegalArgumentException("Le délai d'alerte doit être compris entre 1 et 3650 jours.");
        boolean duplicate=repo.findAll().stream().anyMatch(p -> !p.getId().equals(exclude) && p.getJoursAvant().equals(jours));
        if (duplicate) throw new IllegalArgumentException("Un délai d'alerte de "+jours+" jours existe déjà.");
    }
    private void auditAdmin(String action,String oldValue,String newValue) { Object principal=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getPrincipal(); Integer id=principal instanceof Integer?(Integer)principal:null; audit.record(id,null,null,action,oldValue,newValue); }
}
