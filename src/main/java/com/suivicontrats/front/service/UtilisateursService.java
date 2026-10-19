
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.dto.UtilisateursDTO;
import com.suivicontrats.front.repository.UtilisateursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilisateursService {

    private final UtilisateursRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<Utilisateurs> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Utilisateurs getOne(Integer id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur non trouvé"));
    }

    @Transactional
    public Utilisateurs create(Utilisateurs utilisateur) {
        utilisateur.setId(null);

        if (utilisateur.getMotDePasse() == null
                || utilisateur.getMotDePasse().isBlank()) {
            throw new IllegalArgumentException(
                    "Le mot de passe est obligatoire à la création");
        }

        if (utilisateur.getMatricule() == null) {
            throw new IllegalArgumentException(
                    "Le matricule est obligatoire");
        }

        String motDePasseClair = utilisateur.getMotDePasse();

        utilisateur.setMotDePasse(
                passwordEncoder.encode(motDePasseClair));

        Utilisateurs savedUser = repository.save(utilisateur);

        try {
            emailService.envoyerEmailCreationCompte(
                    savedUser.getEmail(),
                    nomComplet(savedUser),
                    String.valueOf(savedUser.getMatricule()),
                    motDePasseClair
            );
        } catch (RuntimeException e) {
            log.error(
                    "Compte créé, mais échec de l'envoi de l'e-mail à {}",
                    savedUser.getEmail(),
                    e
            );
        }

        return savedUser;
    }

    @Transactional
    public UtilisateursDTO updateProfile(Integer id, ProfilRequest details) {
        Utilisateurs utilisateur = getOne(id);
        String email = details.getEmail().trim();
        if (repository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée.");
        }
        utilisateur.setNom(details.getNom().trim());
        utilisateur.setPrenom(details.getPrenom().trim());
        utilisateur.setEmail(email);
        utilisateur.setService(details.getService() == null ? null : details.getService().trim());
        Utilisateurs saved = repository.save(utilisateur);
        return UtilisateursDTO.builder()
                .id(saved.getId())
                .matricule(saved.getMatricule())
                .nom(saved.getNom())
                .prenom(saved.getPrenom())
                .email(saved.getEmail())
                .role(saved.getRole() == null ? null : saved.getRole().name())
                .service(saved.getService())
                .actif(saved.getActif())
                .build();
    }

    @Transactional
    public Utilisateurs update(Integer id, Utilisateurs details) {
        Utilisateurs utilisateur = getOne(id);

        // Conserver l'ancien hash avant toute modification.
        String ancienHash = utilisateur.getMotDePasse();
        String ancienneAdresse = utilisateur.getEmail();

        if (details.getMatricule() != null) {
            utilisateur.setMatricule(details.getMatricule());
        }

        if (details.getNom() != null) {
            utilisateur.setNom(details.getNom());
        }

        if (details.getPrenom() != null) {
            utilisateur.setPrenom(details.getPrenom());
        }

        if (details.getEmail() != null) {
            utilisateur.setEmail(details.getEmail());
        }

        if (details.getRole() != null) {
            utilisateur.setRole(details.getRole());
        }

        utilisateur.setService(details.getService());

        if (details.getActif() != null) {
            utilisateur.setActif(details.getActif());
        }

        String nouveauMotDePasse = details.getMotDePasse();
        boolean motDePasseModifie = false;

        if (nouveauMotDePasse != null
                && !nouveauMotDePasse.isBlank()) {

            // Accepter un nouveau mot de passe en clair,
            // mais ne jamais remplacer le hash par un hash reçu.
            boolean estDejaUnHash =
                    nouveauMotDePasse.startsWith("$2a$")
                    || nouveauMotDePasse.startsWith("$2b$")
                    || nouveauMotDePasse.startsWith("$2y$");

            if (!estDejaUnHash) {
                motDePasseModifie =
                        ancienHash == null
                        || !passwordEncoder.matches(
                                nouveauMotDePasse, ancienHash);

                utilisateur.setMotDePasse(
                        passwordEncoder.encode(nouveauMotDePasse));
            }
        }

        Utilisateurs savedUser = repository.save(utilisateur);

        boolean adresseModifiee = details.getEmail() != null
                && !details.getEmail().equalsIgnoreCase(ancienneAdresse);

        if (adresseModifiee) {
            try {
                emailService.envoyerEmailModificationAdresse(
                        ancienneAdresse,
                        savedUser.getEmail(),
                        nomComplet(savedUser)
                );
            } catch (RuntimeException e) {
                log.error(
                        "Adresse modifiée, mais échec de notification pour {}",
                        savedUser.getEmail(),
                        e
                );
            }
        }

        if (motDePasseModifie) {
            try {
                emailService.envoyerEmailModificationMotDePasse(
                        savedUser.getEmail(),
                        nomComplet(savedUser),
                        nouveauMotDePasse
                );
            } catch (RuntimeException e) {
                log.error(
                        "Mot de passe modifié, mais échec de notification pour {}",
                        savedUser.getEmail(),
                        e
                );
            }
        }

        return savedUser;
    }

    @Transactional
    public Utilisateurs save(Utilisateurs utilisateur) {
        if (utilisateur.getId() == null) {
            return create(utilisateur);
        }

        return update(utilisateur.getId(), utilisateur);
    }

    @Transactional
    public void delete(Integer id) {
        Utilisateurs utilisateur = getOne(id);

        try {
            emailService.envoyerEmailSuppressionCompte(
                    utilisateur.getEmail(),
                    nomComplet(utilisateur)
            );
        } catch (RuntimeException e) {
            log.error(
                    "Utilisateur {} supprimé, mais échec de notification e-mail",
                    id,
                    e
            );
        }

        repository.delete(utilisateur);
    }

    private String nomComplet(Utilisateurs utilisateur) {
        return utilisateur.getPrenom() + " " + utilisateur.getNom();
    }
}
