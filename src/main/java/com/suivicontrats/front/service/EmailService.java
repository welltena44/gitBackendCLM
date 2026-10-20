
package com.suivicontrats.front.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
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

    @Value("${spring.mail.password}")
    private String motDePasseExpediteur;

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

                Vous devrez choisir un nouveau mot de passe après votre première connexion.
                Ce mot de passe provisoire ne peut servir qu'à cette première étape.
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

                Un administrateur a réinitialisé le mot de passe de votre compte SPAT-CLM.

                Votre nouveau mot de passe : %s

                Connectez-vous avec votre matricule et ce mot de passe provisoire, puis choisissez
                un nouveau mot de passe avant d'accéder à votre espace.

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

    public void envoyerEmailModificationCompte(
            String destinataire,
            String nomComplet) {

        String sujet = "SPAT-CLM | Modification de votre compte";
        String contenu = """
                Bonjour %s,

                Les informations de votre compte SPAT-CLM ont été modifiées
                par un administrateur.

                Si vous n'êtes pas à l'origine de cette modification,
                veuillez contacter l'administration SPAT-CLM.

                Cordialement,
                Administration SPAT-CLM
                """.formatted(nomComplet);

        envoyer(destinataire, sujet, contenu);
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
            log.warn("E-mail {} ignoré : adresse du destinataire absente", sujet);
            return;
        }

        if (adresseExpediteur == null || adresseExpediteur.isBlank()
                || motDePasseExpediteur == null || motDePasseExpediteur.isBlank()) {
            throw new MailAuthenticationException(
                    "E-mail non envoyé : configurez MAIL_USERNAME et MAIL_PASSWORD dans l'environnement du backend.");
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
            log.error("Échec SMTP pour l'e-mail destiné à {} : {}", destinataire, e.getMessage());
            throw e;
        }
    }
}
