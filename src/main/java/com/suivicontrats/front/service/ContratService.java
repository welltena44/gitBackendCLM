package com.suivicontrats.front.service;
import com.suivicontrats.front.entity.Contrat;
import com.suivicontrats.front.repository.ContratRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContratService {
    private final ContratRepository contratRepo;

    public List<Contrat> getAll() { return contratRepo.findAll(); }
    public Contrat getById(Integer id) { return contratRepo.findById(id).orElseThrow(() -> new RuntimeException("Contrat non trouvé")); }
    
    public Contrat create(Contrat contrat) {
        contrat.setId(null);
        if(contratRepo.existsByReference(contrat.getReference())){
            throw new RuntimeException("Référence déjà existante");
        }
        return contratRepo.save(contrat);
    }
    public Contrat update(Integer id, Contrat details) {
        Contrat c = getById(id);
        if (details.getReference() == null || details.getReference().isBlank()) {
            throw new IllegalArgumentException("Le numéro de contrat est obligatoire");
        }
        if (!c.getReference().equals(details.getReference())
                && contratRepo.existsByReference(details.getReference())) {
            throw new RuntimeException("Référence déjà existante");
        }
        c.setReference(details.getReference());
        c.setObjet(details.getObjet());
        c.setDescription(details.getDescription());
        c.setClauses(details.getClauses());
        c.setMontant(details.getMontant());
        c.setDevise(details.getDevise());
        c.setDateSignature(details.getDateSignature());
        c.setStatut(details.getStatut());
        c.setDateDebut(details.getDateDebut());
        c.setDateFin(details.getDateFin());
        c.setDureeMois(details.getDureeMois());
        c.setService(details.getService());
        c.setRenouvellementAutomatique(details.getRenouvellementAutomatique());
        c.setJoursPreavis(details.getJoursPreavis());
        c.setResponsableId(details.getResponsableId());
        c.setTypeId(details.getTypeId());
        return contratRepo.save(c);
    }
    public void delete(Integer id) { contratRepo.deleteById(id); }
}
