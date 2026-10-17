package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Validations;
import com.suivicontrats.front.repository.ValidationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationsService {

    private final ValidationsRepository validationsRepo;

    public List<Validations> getByContrat(Integer contratId){
        return validationsRepo.findByContratId(contratId);
    }

    public Validations getOne(Long id) {
        return validationsRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));
    }

    public List<Validations> getEnAttente(){ 
        return validationsRepo.findByDecision("EN_ATTENTE"); 
    }

    public List<Validations> genererCircuit(Integer contratId, Integer demandeurId){
        Integer cycle = validationsRepo.findTopByContratIdOrderByCycleDesc(contratId)
                .map(previous -> previous.getCycle() + 1)
                .orElse(1);
        Validations v = Validations.builder()
                .contratId(contratId)
                .demandeurId(demandeurId)
                .niveau(1)
                .roleRequis("VALIDEUR")
                .cycle(cycle)
                .decision("EN_ATTENTE")
                .dateSoumission(LocalDateTime.now())
                .build();
        return validationsRepo.saveAll(List.of(v));
    }

    public Validations decider(Long validationId, Integer valideurId, String decision, String commentaire){
        if (!List.of("APPROUVE", "REJETE", "MODIFICATION_DEMANDEE").contains(decision)) {
            throw new IllegalArgumentException("Décision de validation invalide");
        }
        Validations v = getOne(validationId);
        v.setValideurId(valideurId);
        v.setDecision(decision);
        v.setCommentaire(commentaire);
        v.setDateValidation(LocalDateTime.now());
        return validationsRepo.save(v);
    }

    public List<Validations> all(){
        return validationsRepo.findAll();
    }

    public Validations create(Validations validation) {
        validation.setId(null);
        return validationsRepo.save(validation);
    }

    public Validations update(Long id, Validations details) {
        Validations validation = getOne(id);
        validation.setContratId(details.getContratId());
        validation.setValideurId(details.getValideurId());
        validation.setDemandeurId(details.getDemandeurId());
        validation.setNiveau(details.getNiveau());
        validation.setRoleRequis(details.getRoleRequis());
        validation.setCycle(details.getCycle());
        validation.setDecision(details.getDecision());
        validation.setCommentaire(details.getCommentaire());
        validation.setDateSoumission(details.getDateSoumission());
        validation.setDateValidation(details.getDateValidation());
        return validationsRepo.save(validation);
    }

    public void delete(Long id) {
        validationsRepo.delete(getOne(id));
    }
}
