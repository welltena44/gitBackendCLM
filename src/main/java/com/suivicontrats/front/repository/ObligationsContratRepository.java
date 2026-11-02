package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.ObligationsContrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObligationsContratRepository extends JpaRepository<ObligationsContrat, Long> {
    List<ObligationsContrat> findByContratIdOrderByDateCreationDesc(Integer contratId);
    boolean existsByContratIdAndStatut(Integer contratId, String statut);
}
