package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    List<Notifications> findByUtilisateurIdAndLuOrderByDateCreationDesc(Integer utilisateurId, Boolean lu);
    List<Notifications> findByUtilisateurIdOrderByDateCreationDesc(Integer utilisateurId);
    long countByUtilisateurIdAndLuFalse(Integer utilisateurId);
    List<Notifications> findByContratId(Integer contratId);
}