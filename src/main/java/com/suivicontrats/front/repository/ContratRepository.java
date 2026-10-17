package com.suivicontrats.front.repository;
import com.suivicontrats.front.entity.Contrat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContratRepository extends JpaRepository<Contrat, Integer> {
    List<Contrat> findByResponsableId(Integer responsableId);
    List<Contrat> findByStatut(String statut);
    List<Contrat> findByService(String service);
    boolean existsByReference(String reference);
}