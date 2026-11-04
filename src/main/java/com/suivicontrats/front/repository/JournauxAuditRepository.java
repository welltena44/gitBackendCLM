package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.JournauxAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface JournauxAuditRepository extends JpaRepository<JournauxAudit, Long> {
    List<JournauxAudit> findByContratIdOrderByDateActionDesc(Integer contratId);
    List<JournauxAudit> findByUtilisateurIdOrderByDateActionDesc(Integer utilisateurId);
    List<JournauxAudit> findAllByOrderByDateActionDesc();

    @Query("SELECT j FROM JournauxAudit j WHERE (:userId IS NULL OR j.utilisateurId = :userId) " +
           "AND (:action IS NULL OR LOWER(j.action) LIKE LOWER(CONCAT('%', :action, '%'))) " +
           "AND (:fromDate IS NULL OR j.dateAction >= :fromDate) " +
           "AND (:toDate IS NULL OR j.dateAction <= :toDate) " +
           "ORDER BY j.dateAction DESC")
    Page<JournauxAudit> search(@Param("userId") Integer userId, @Param("action") String action,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate, Pageable pageable);
}
