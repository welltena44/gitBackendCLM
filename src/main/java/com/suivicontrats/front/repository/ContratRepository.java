package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Contrat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface ContratRepository extends JpaRepository<Contrat, Integer> {
    // Sérialise les décisions concurrentes portant sur un même contrat.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contrat c where c.id = :id")
    Optional<Contrat> findByIdForUpdate(@Param("id") Integer id);

    List<Contrat> findByResponsableId(Integer responsableId);

    List<Contrat> findByStatut(String statut);

    List<Contrat> findByStatutIn(Collection<String> statuts);

    List<Contrat> findByService(String service);

    boolean existsByReference(String reference);

    boolean existsByTypeId(Long typeId);

    boolean existsByResponsableId(Integer responsableId);

    @Modifying
    @Query("update Contrat c set c.service = :service where c.responsableId = :responsableId")
    int updateServiceByResponsableId(
            @Param("responsableId") Integer responsableId,
            @Param("service") String service);
}