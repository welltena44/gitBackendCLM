package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.Avenants;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvenantsRepository extends JpaRepository<Avenants, Long> {
    java.util.Optional<Avenants> findTopByContratIdOrderByNumeroDesc(Integer contratId);
    List<Avenants> findByContratIdOrderByNumeroAsc(Integer contratId);
    List<Avenants> findByContratId(Integer contratId);
}
