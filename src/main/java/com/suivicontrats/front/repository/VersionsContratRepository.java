package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.VersionsContrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VersionsContratRepository extends JpaRepository<VersionsContrat, Long> {
    List<VersionsContrat> findByContratIdOrderByVersionAsc(Integer contratId);
    Optional<VersionsContrat> findTopByContratIdOrderByVersionDesc(Integer contratId);
    List<VersionsContrat> findByContratId(Integer contratId);
}