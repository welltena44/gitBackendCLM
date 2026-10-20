package com.suivicontrats.front.repository;

import com.suivicontrats.front.entity.DemandesReinitialisationMotDePasse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DemandesReinitialisationMotDePasseRepository
        extends JpaRepository<DemandesReinitialisationMotDePasse, Long> {
    List<DemandesReinitialisationMotDePasse> findByStatutOrderByDateCreationAsc(
            DemandesReinitialisationMotDePasse.Statut statut);
    Optional<DemandesReinitialisationMotDePasse> findFirstByUtilisateur_IdAndStatut(
            Integer utilisateurId, DemandesReinitialisationMotDePasse.Statut statut);
}
