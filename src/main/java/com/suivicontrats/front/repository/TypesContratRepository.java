package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.TypesContrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TypesContratRepository extends JpaRepository<TypesContrat, Long> {
    List<TypesContrat> findByActifTrue();
    List<TypesContrat> findAllByOrderByNomAsc();
}