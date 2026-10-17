package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Utilisateurs;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UtilisateursRepository extends JpaRepository<Utilisateurs, Integer> {
    Optional<Utilisateurs> findByMatricule(Integer matricule);
    Optional<Utilisateurs> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
