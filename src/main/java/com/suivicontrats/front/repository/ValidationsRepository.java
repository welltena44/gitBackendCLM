package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Validations;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ValidationsRepository extends JpaRepository<Validations, Long> {
    List<Validations> findByContratId(Integer contratId);
    List<Validations> findByDecision(String decision);
    List<Validations> findByValideurId(Integer valideurId);
    boolean existsByValideurId(Integer valideurId);
    List<Validations> findByValideurIdIsNullAndRoleRequis(String roleRequis);
    java.util.Optional<Validations> findTopByContratIdOrderByCycleDesc(Integer contratId);
    List<Validations> findByContratIdAndCycleOrderByNiveauAsc(Integer contratId, Integer cycle);
    List<Validations> findByAvenantIdAndCycleOrderByNiveauAsc(Long avenantId, Integer cycle);
    java.util.Optional<Validations> findTopByAvenantIdOrderByCycleDesc(Long avenantId);
    boolean existsByAvenantIdAndDecisionIn(Long avenantId, java.util.Collection<String> decisions);
}
