package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.ParametresAlertes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParametresAlertesRepository extends JpaRepository<ParametresAlertes, Long> {
    List<ParametresAlertes> findByActifTrueOrderByJoursAvantAsc();
    List<ParametresAlertes> findAllByOrderByJoursAvantAsc();
}