
package com.suivicontrats.front.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adresseExpediteur;

    public void envoyerEmailCreationCompte(
            String destinataire,
            String nomComplet,
            String login,
            String motDePasseProvisoire) {

        String sujet = "SPAT-CLM | Création de votre compte";

        String contenu = """
                Bonjour %s,

                Votre compte SPAT-CLM a été créé avec succès.

                Votre matricule de connexion : %s
                Votre mot de passe provisoire : %s

                Pour votre sécurité, changez ce mot de passe après votre
                première connexion et ne partagez pas vos identifiants.
                Si vous n'êtes pas à l'origine de cette création,
                veuillez contacter l'administrateur.

                Cordialement,
                Administration SPAT-CLM
                """.formatted(nomComplet, login, motDePasseProvisoire);

        envoyer(destinataire, sujet, contenu);
    }

    public void envoyerEmailModificationMotDePasse(
            String destinataire,
            String nomComplet,
            String nouveauMotDePasse) {

        String sujet = "SPAT-CLM | Modification de votre mot de passe";

        String contenu = """
                Bonjour %s,

                Le mot de passe de votre compte SPAT-CLM a été modifié
                par l'administrateur.

                Votre nouveau mot de passe : %s

                Connectez-vous avec votre matricule et ce nouveau mot de passe.

                Si vous n'êtes pas à l'origine de cette modification,
                veuillez contacter immédiatement l'administrateur.

                Cordialement,
                Administration SPAT-CLM
                """.formatted(nomComplet, nouveauMotDePasse);

        envoyer(destinataire, sujet, contenu);
    }

    public void envoyerEmailModificationAdresse(
            String ancienneAdresse,
            String nouvelleAdresse,
            String nomComplet) {

        String sujet = "SPAT-CLM | Modification de votre adresse e-mail";
        String contenu = """
                Bonjour %s,

                L'adresse e-mail de votre compte SPAT-CLM a été modifiée.
                Cette adresse est désormais utilisée pour les notifications.
                Si vous n'êtes pas à l'origine de cette modification,
                veuillez contacter l'administrateur.

                Cordialement,
                Administration SPAT-CLM
                """.formatted(nomComplet);

        envoyer(nouvelleAdresse, sujet, contenu);
        if (ancienneAdresse != null && !ancienneAdresse.isBlank()) {
            envoyer(ancienneAdresse, sujet, contenu);
        }
    }

    public void envoyerEmailSuppressionCompte(
            String destinataire,
            String nomComplet) {

        String sujet = "SPAT-CLM | Suppression de votre compte";

        String contenu = """
                Bonjour %s,

                Nous vous informons que votre compte SPAT-CLM
                a été supprimé par l'administrateur.

                Vous ne pourrez désormais plus vous connecter
                à la plateforme avec ce compte.

                Pour toute question, veuillez contacter
                l'administration SPAT-CLM.

                Cordialement,
                Administration SPAT-CLM
                """.formatted(nomComplet);

        envoyer(destinataire, sujet, contenu);
    }

    private void envoyer(
            String destinataire,
            String sujet,
            String contenu) {

        if (destinataire == null || destinataire.isBlank()) {
            throw new IllegalArgumentException(
                    "L'adresse e-mail du destinataire est obligatoire.");
        }

        if (adresseExpediteur == null || adresseExpediteur.isBlank()) {
            throw new IllegalStateException(
                    "La configuration spring.mail.username est absente.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(adresseExpediteur);
        message.setTo(destinataire);
        message.setSubject(sujet);
        message.setText(contenu);

        try {
            mailSender.send(message);
            log.info("E-mail envoyé à {}", destinataire);
        } catch (MailException e) {
            log.error(
                    "Échec de l'envoi de l'e-mail à {}",
                    destinataire,
                    e
            );
            throw e;
        }
    }
}
