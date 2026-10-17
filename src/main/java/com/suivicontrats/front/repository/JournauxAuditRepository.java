package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.JournauxAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JournauxAuditRepository extends JpaRepository<JournauxAudit, Long> {
    List<JournauxAudit> findByContratIdOrderByDateActionDesc(Integer contratId);
    List<JournauxAudit> findByUtilisateurIdOrderByDateActionDesc(Integer utilisateurId);
    List<JournauxAudit> findAllByOrderByDateActionDesc();
}