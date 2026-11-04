
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.dto.ProfilRequest;
import com.suivicontrats.front.dto.UtilisateursDTO;
import com.suivicontrats.front.repository.ContratRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import com.suivicontrats.front.repository.ValidationsRepository;
import com.suivicontrats.front.repository.DemandesReinitialisationMotDePasseRepository;
import com.suivicontrats.front.entity.DemandesReinitialisationMotDePasse;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ContratRepository contratRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ValidationsRepository validationsRepository;
    private final DemandesReinitialisationMotDePasseRepository demandesRepository;
    private final JournauxAuditService auditService;

    @Transactional(readOnly = true)
    public List<Utilisateurs> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Utilisateurs getOne(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    @Transactional
    public Utilisateurs create(Utilisateurs utilisateur) {
        utilisateur.setId(null);

        validerUtilisateur(utilisateur, null);

        String motDePasseClair = genererMotDePasse();
        utilisateur.setMotDePasse(
                passwordEncoder.encode(motDePasseClair));
        utilisateur.setChangementMotDePasseRequis(true);

        Utilisateurs savedUser = repository.save(utilisateur);
        audit("UTILISATEUR_CREE", null, "id=" + savedUser.getId() + "; matricule=" + savedUser.getMatricule() + "; role=" + savedUser.getRole());

        emailService.envoyerEmailCreationCompte(
                savedUser.getEmail(),
                nomComplet(savedUser),
                String.valueOf(savedUser.getMatricule()),
                motDePasseClair);

        return savedUser;
    }

    @Transactional
    public UtilisateursDTO updateProfile(Integer id, ProfilRequest details) {
        Utilisateurs utilisateur = getOne(id);
        String ancienneAdresse = utilisateur.getEmail();
        String ancienService = utilisateur.getService();
        String email = details.getEmail().trim();
        if (repository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée.");
        }
        utilisateur.setNom(details.getNom().trim());
        utilisateur.setPrenom(details.getPrenom().trim());
        utilisateur.setEmail(email);
        utilisateur.setService(normaliserService(details.getService()));
        Utilisateurs saved = repository.save(utilisateur);
        synchroniserServiceContrats(id, ancienService, saved.getService());

        if (!email.equalsIgnoreCase(ancienneAdresse)) {
            emailService.envoyerEmailModificationAdresse(
                    ancienneAdresse,
                    email,
                    nomComplet(saved));
        } else {
            emailService.envoyerEmailModificationCompte(
                    email,
                    nomComplet(saved));
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
        validerUtilisateur(details, id);
        protegerDernierAdmin(utilisateur, details.getRole(), details.getActif());
        if (details.getActif() != null && !details.getActif() && id.equals(currentUserId()))
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");
        String ancienneDescription = "matricule=" + utilisateur.getMatricule() + "; role=" + utilisateur.getRole() + "; actif=" + utilisateur.getActif();

        // Conserver l'ancien hash avant toute modification.
        String ancienHash = utilisateur.getMotDePasse();
        String ancienneAdresse = utilisateur.getEmail();
        String ancienService = utilisateur.getService();

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

        utilisateur.setService(normaliserService(details.getService()));

        if (details.getActif() != null) {
            utilisateur.setActif(details.getActif());
        }

        String nouveauMotDePasse = details.getMotDePasse();
        boolean motDePasseModifie = false;

        if (nouveauMotDePasse != null
                && !nouveauMotDePasse.isBlank()) {

            // Accepter un nouveau mot de passe en clair,
            // mais ne jamais remplacer le hash par un hash reçu.
            boolean estDejaUnHash = nouveauMotDePasse.startsWith("$2a$")
                    || nouveauMotDePasse.startsWith("$2b$")
                    || nouveauMotDePasse.startsWith("$2y$");

            if (!estDejaUnHash) {
                motDePasseModifie = ancienHash == null
                        || !passwordEncoder.matches(
                                nouveauMotDePasse, ancienHash);

                nouveauMotDePasse = genererMotDePasse();
                utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
                utilisateur.setChangementMotDePasseRequis(true);
            }
        }

        Utilisateurs savedUser = repository.save(utilisateur);
        synchroniserServiceContrats(id, ancienService, savedUser.getService());
        audit("UTILISATEUR_MODIFIE", ancienneDescription, "matricule=" + savedUser.getMatricule() + "; role=" + savedUser.getRole() + "; actif=" + savedUser.getActif());

        boolean adresseModifiee = details.getEmail() != null
                && !details.getEmail().equalsIgnoreCase(ancienneAdresse);

        if (adresseModifiee) {
            emailService.envoyerEmailModificationAdresse(
                    ancienneAdresse,
                    savedUser.getEmail(),
                    nomComplet(savedUser));
        }

        if (motDePasseModifie) {
            emailService.envoyerEmailModificationMotDePasse(
                    savedUser.getEmail(),
                    nomComplet(savedUser),
                    nouveauMotDePasse);
        }

        if (!adresseModifiee && !motDePasseModifie) {
            emailService.envoyerEmailModificationCompte(
                    savedUser.getEmail(),
                    nomComplet(savedUser));
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
        for (int i = 0; i < 8; i++)
            value.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return value.toString();
    }

    private String normaliserService(String service) {
        if (service == null || service.isBlank())
            return null;
        return service.trim();
    }

    private void synchroniserServiceContrats(Integer responsableId, String ancienService, String nouveauService) {
        if (!java.util.Objects.equals(ancienService, nouveauService))
            contratRepository.updateServiceByResponsableId(responsableId, nouveauService);
    }

    @Transactional
    public void reinitialiserMotDePasse(Integer id) {
        Utilisateurs utilisateur = getOne(id);
        String motDePasseProvisoire = genererMotDePasse();
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasseProvisoire));
        utilisateur.setChangementMotDePasseRequis(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouilleJusqua(null);
        repository.save(utilisateur);
        audit("MOT_DE_PASSE_REINITIALISE", null, "utilisateurId=" + id);
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
        if (id.equals(currentUserId())) throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte.");
        if (utilisateur.getRole() == Utilisateurs.Role.ADMIN && Boolean.TRUE.equals(utilisateur.getActif())
                && repository.countByRoleAndActifTrue(Utilisateurs.Role.ADMIN) <= 1)
            throw new IllegalArgumentException("Impossible de supprimer le dernier administrateur actif.");
        if (contratRepository.existsByResponsableId(id) || validationsRepository.existsByValideurId(id)
                || demandesRepository.existsByUtilisateur_Id(id))
            throw new IllegalArgumentException("Cet utilisateur est encore référencé par des contrats, validations ou demandes. Désactivez son compte au lieu de le supprimer.");

        emailService.envoyerEmailSuppressionCompte(
                utilisateur.getEmail(),
                nomComplet(utilisateur));

        repository.delete(utilisateur);
        audit("UTILISATEUR_SUPPRIME", "id=" + id + "; matricule=" + utilisateur.getMatricule(), null);
    }

    @Transactional(readOnly = true)
    public List<Utilisateurs> comptesVerrouilles() {
        return repository.findByVerrouilleJusquaAfter(java.time.LocalDateTime.now());
    }

    @Transactional
    public void deverrouiller(Integer id) {
        Utilisateurs u = getOne(id);
        u.setTentativesEchec(0); u.setVerrouilleJusqua(null); repository.save(u);
        audit("COMPTE_DEVERROUILLE", null, "utilisateurId=" + id);
    }

    private void validerUtilisateur(Utilisateurs u, Integer excludeId) {
        if (u.getMatricule() == null || u.getMatricule() <= 0) throw new IllegalArgumentException("Le matricule doit être un entier positif.");
        if (repository.findByMatricule(u.getMatricule()).filter(x -> !x.getId().equals(excludeId)).isPresent()) throw new IllegalArgumentException("Ce matricule est déjà utilisé.");
        if (u.getEmail() == null || !u.getEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Veuillez saisir une adresse e-mail valide.");
        if (repository.findByEmailIgnoreCase(u.getEmail().trim()).filter(x -> !x.getId().equals(excludeId)).isPresent()) throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée.");
        if (u.getRole() == null) throw new IllegalArgumentException("Le rôle de l'utilisateur est obligatoire.");
        if (u.getNom() == null || u.getNom().isBlank() || u.getPrenom() == null || u.getPrenom().isBlank()) throw new IllegalArgumentException("Le nom et le prénom sont obligatoires.");
    }

    private void protegerDernierAdmin(Utilisateurs current, Utilisateurs.Role nextRole, Boolean nextActive) {
        boolean adminActif = current.getRole() == Utilisateurs.Role.ADMIN && Boolean.TRUE.equals(current.getActif());
        boolean resteAdmin = nextRole == Utilisateurs.Role.ADMIN && (nextActive == null || nextActive);
        if (adminActif && !resteAdmin && repository.countByRoleAndActifTrue(Utilisateurs.Role.ADMIN) <= 1)
            throw new IllegalArgumentException("Impossible de désactiver ou rétrograder le dernier administrateur actif.");
    }
    private Integer currentUserId() { Object p=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getPrincipal(); return p instanceof Integer?(Integer)p:null; }
    private void audit(String action,String oldValue,String newValue) { auditService.record(currentUserId(),null,null,action,oldValue,newValue); }

    private String nomComplet(Utilisateurs utilisateur) {
        return utilisateur.getPrenom() + " " + utilisateur.getNom();
    }
}
