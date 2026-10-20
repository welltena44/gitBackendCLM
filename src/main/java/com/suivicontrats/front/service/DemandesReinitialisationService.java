package com.suivicontrats.front.service;

import com.suivicontrats.front.dto.DemandeReinitialisationDTO;
import com.suivicontrats.front.entity.DemandesReinitialisationMotDePasse;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.DemandesReinitialisationMotDePasseRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemandesReinitialisationService {
    private final DemandesReinitialisationMotDePasseRepository demandesRepository;
    private final UtilisateursRepository utilisateursRepository;
    private final UtilisateursService utilisateursService;

    @Transactional
    public void soumettre(Integer matricule) {
        if (matricule == null) return;
        utilisateursRepository.findByMatricule(matricule)
                .filter(u -> Boolean.TRUE.equals(u.getActif()))
                .ifPresent(u -> demandesRepository.findFirstByUtilisateur_IdAndStatut(
                        u.getId(), DemandesReinitialisationMotDePasse.Statut.EN_ATTENTE)
                        .orElseGet(() -> demandesRepository.save(
                                DemandesReinitialisationMotDePasse.builder().utilisateur(u).build())));
    }

    @Transactional(readOnly = true)
    public List<DemandeReinitialisationDTO> enAttente(Integer adminId) {
        verifierAdministrateur(adminId);
        return demandesRepository.findByStatutOrderByDateCreationAsc(
                        DemandesReinitialisationMotDePasse.Statut.EN_ATTENTE)
                .stream()
                .map(d -> new DemandeReinitialisationDTO(
                        d.getId(), d.getUtilisateur().getMatricule(),
                        d.getUtilisateur().getPrenom() + " " + d.getUtilisateur().getNom(), d.getDateCreation()))
                .toList();
    }

    @Transactional
    public void approuver(Long demandeId, Integer adminId) {
        verifierAdministrateur(adminId);
        DemandesReinitialisationMotDePasse demande = demandeEnAttente(demandeId);
        utilisateursService.reinitialiserMotDePasse(demande.getUtilisateur().getId());
        demande.setStatut(DemandesReinitialisationMotDePasse.Statut.APPROUVEE);
        demandesRepository.save(demande);
    }

    @Transactional
    public void rejeter(Long demandeId, Integer adminId) {
        verifierAdministrateur(adminId);
        DemandesReinitialisationMotDePasse demande = demandeEnAttente(demandeId);
        demande.setStatut(DemandesReinitialisationMotDePasse.Statut.REJETEE);
        demandesRepository.save(demande);
    }

    private DemandesReinitialisationMotDePasse demandeEnAttente(Long id) {
        DemandesReinitialisationMotDePasse demande = demandesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
        if (demande.getStatut() != DemandesReinitialisationMotDePasse.Statut.EN_ATTENTE) {
            throw new IllegalArgumentException("Cette demande a déjà été traitée.");
        }
        return demande;
    }

    private void verifierAdministrateur(Integer adminId) {
        Utilisateurs admin = adminId == null ? null : utilisateursRepository.findById(adminId).orElse(null);
        if (admin == null || admin.getRole() != Utilisateurs.Role.ADMIN || !Boolean.TRUE.equals(admin.getActif())) {
            throw new SecurityException("Accès réservé à l'administrateur.");
        }
    }
}
