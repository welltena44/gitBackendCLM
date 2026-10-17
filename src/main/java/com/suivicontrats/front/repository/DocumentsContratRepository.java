package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.DocumentsContrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentsContratRepository extends JpaRepository<DocumentsContrat, Long> {
    List<DocumentsContrat> findByContratId(Integer contratId);
    List<DocumentsContrat> findByContratIdOrderByDateImportDesc(Integer contratId);
}