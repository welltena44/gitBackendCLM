
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.dto.UtilisateursDTO;
import com.suivicontrats.front.repository.UtilisateursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UtilisateursService {
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

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

        if (utilisateur.getMatricule() == null) {
            throw new IllegalArgumentException(
                    "Le matricule est obligatoire");
        }

        String motDePasseClair = genererMotDePasse();
        utilisateur.setMotDePasse(
                passwordEncoder.encode(motDePasseClair));
        utilisateur.setChangementMotDePasseRequis(true);

        Utilisateurs savedUser = repository.save(utilisateur);

        emailService.envoyerEmailCreationCompte(
            savedUser.getEmail(),
            nomComplet(savedUser),
            String.valueOf(savedUser.getMatricule()),
            motDePasseClair
        );

        return savedUser;
    }

    @Transactional
    public UtilisateursDTO updateProfile(Integer id, ProfilRequest details) {
        Utilisateurs utilisateur = getOne(id);
        String ancienneAdresse = utilisateur.getEmail();
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

        if (!email.equalsIgnoreCase(ancienneAdresse)) {
            emailService.envoyerEmailModificationAdresse(
                ancienneAdresse,
                email,
                nomComplet(saved)
            );
        } else {
            emailService.envoyerEmailModificationCompte(
                email,
                nomComplet(saved)
            );
        }

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

                nouveauMotDePasse = genererMotDePasse();
                utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
                utilisateur.setChangementMotDePasseRequis(true);
            }
        }

        Utilisateurs savedUser = repository.save(utilisateur);

        boolean adresseModifiee = details.getEmail() != null
                && !details.getEmail().equalsIgnoreCase(ancienneAdresse);

        if (adresseModifiee) {
            emailService.envoyerEmailModificationAdresse(
                    ancienneAdresse,
                    savedUser.getEmail(),
                    nomComplet(savedUser)
            );
        }

        if (motDePasseModifie) {
            emailService.envoyerEmailModificationMotDePasse(
                    savedUser.getEmail(),
                    nomComplet(savedUser),
                    nouveauMotDePasse
            );
        }

        if (!adresseModifiee && !motDePasseModifie) {
            emailService.envoyerEmailModificationCompte(
                    savedUser.getEmail(),
                    nomComplet(savedUser)
            );
        }

        return savedUser;
    }

    @Transactional
    public void changerMotDePasse(Integer id, String motDePasseActuel, String nouveauMotDePasse) {
        Utilisateurs utilisateur = getOne(id);
        if (motDePasseActuel == null || !passwordEncoder.matches(motDePasseActuel, utilisateur.getMotDePasse())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8 || nouveauMotDePasse.length() > 128) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit contenir entre 8 et 128 caractères.");
        }
        utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateur.setChangementMotDePasseRequis(false);
        repository.save(utilisateur);
    }

    private String genererMotDePasse() {
        StringBuilder value = new StringBuilder(8);
        for (int i = 0; i < 8; i++) value.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return value.toString();
    }

    @Transactional
    public void reinitialiserMotDePasse(Integer id) {
        Utilisateurs utilisateur = getOne(id);
        String motDePasseProvisoire = genererMotDePasse();
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasseProvisoire));
        utilisateur.setChangementMotDePasseRequis(true);
        repository.save(utilisateur);
        emailService.envoyerEmailModificationMotDePasse(
                utilisateur.getEmail(), nomComplet(utilisateur), motDePasseProvisoire);
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

        emailService.envoyerEmailSuppressionCompte(
            utilisateur.getEmail(),
            nomComplet(utilisateur)
        );

        repository.delete(utilisateur);
    }

    private String nomComplet(Utilisateurs utilisateur) {
        return utilisateur.getPrenom() + " " + utilisateur.getNom();
    }
}
