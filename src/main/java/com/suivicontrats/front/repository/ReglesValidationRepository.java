package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.ReglesValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface ReglesValidationRepository extends JpaRepository<ReglesValidation, Long> {

    List<ReglesValidation> findByTypeContratIdOrderByNiveauAsc(Long typeContratId);
    
    List<ReglesValidation> findAllByOrderByNiveauAsc();

    // Ta logique métier : montant_minimum <= montant < montant_maximum
    @Query("SELECT r FROM ReglesValidation r WHERE " +
           "(r.typeContratId = :typeId OR r.typeContratId IS NULL) AND " +
           "r.montantMinimum <= :montant AND " +
           "(r.montantMaximum IS NULL OR r.montantMaximum > :montant) " +
           "ORDER BY r.niveau ASC")
    List<ReglesValidation> findCircuitApplicable(
            @Param("typeId") Long typeContratId,
            @Param("montant") BigDecimal montant
    );
}