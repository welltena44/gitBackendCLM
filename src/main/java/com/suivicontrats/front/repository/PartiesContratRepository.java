package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.PartiesContrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PartiesContratRepository extends JpaRepository<PartiesContrat, Long> {
    List<PartiesContrat> findByContratId(Integer contratId);
    List<PartiesContrat> findByType(String type);
    List<PartiesContrat> findByNomContainingIgnoreCase(String nom);
}