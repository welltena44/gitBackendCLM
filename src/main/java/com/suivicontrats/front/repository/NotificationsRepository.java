package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    List<Notifications> findByUtilisateurIdAndLuOrderByDateCreationDesc(Integer utilisateurId, Boolean lu);
    List<Notifications> findByUtilisateurIdOrderByDateCreationDesc(Integer utilisateurId);
    long countByUtilisateurIdAndLuFalse(Integer utilisateurId);
    List<Notifications> findByContratId(Integer contratId);
    boolean existsByUtilisateurIdAndContratIdAndParametreAlerteId(Integer utilisateurId, Integer contratId, Long parametreAlerteId);
    @Modifying
    @Query("update Notifications n set n.lu = true where n.utilisateurId = :userId and n.lu = false")
    int markAllRead(@Param("userId") Integer userId);
}
