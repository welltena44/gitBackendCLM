package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.TypesContrat;
import com.suivicontrats.front.repository.TypesContratRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;

/**
 * Lit le texte d'un PDF de contrat et en extrait les champs du formulaire « Nouveau contrat ».
 * Ne renvoie que des valeurs réellement trouvées dans le document : rien n'est inventé.
 */
@Service
@RequiredArgsConstructor
public class ContratPdfExtractionService {

    private static final long TAILLE_MAX = 10L * 1024 * 1024; // 10 Mo
    private static final int PAGES_MAX = 60;

    private final TypesContratRepository typesRepo;

    public record Reponse(ContratPdfParser.Extraction champs, Long typeId, String typeNom) {
    }

    public Reponse extraire(MultipartFile fichier) throws IOException {
        if (fichier == null || fichier.isEmpty())
            throw new IllegalArgumentException("Le fichier envoyé est vide.");
        String nom = fichier.getOriginalFilename() == null ? ""
                : fichier.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nom.endsWith(".pdf"))
            throw new IllegalArgumentException(
                    "Seuls les fichiers PDF sont pris en charge pour le pré-remplissage automatique.");
        if (fichier.getSize() > TAILLE_MAX)
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale de 10 Mo.");

        byte[] octets = fichier.getBytes();
        String entete = new String(octets, 0, Math.min(1024, octets.length), StandardCharsets.ISO_8859_1);
        if (!entete.contains("%PDF-"))
            throw new IllegalArgumentException("Ce fichier n'est pas un PDF valide.");

        String texte = lireTexte(octets);
        if (texte.strip().length() < 30)
            throw new IllegalArgumentException(
                    "Aucun texte exploitable dans ce PDF (probablement un document scanné). "
                            + "Utilisez un PDF généré par ordinateur ou saisissez les champs manuellement.");

        ContratPdfParser.Extraction extraction = ContratPdfParser.parse(texte);
        TypesContrat type = deviner(texte);
        return new Reponse(extraction, type == null ? null : type.getId(), type == null ? null : type.getNom());
    }

    private String lireTexte(byte[] octets) {
        try (PDDocument document = Loader.loadPDF(octets)) {
            int pages = document.getNumberOfPages();
            if (pages > PAGES_MAX)
                throw new IllegalArgumentException(
                        "Ce PDF comporte plus de " + PAGES_MAX + " pages : il est trop volumineux pour l'analyse automatique.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(pages);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Ce PDF est protégé par un mot de passe : il ne peut pas être analysé.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire ce PDF : le fichier est corrompu ou illisible.");
        }
    }

    /** Type de contrat actif dont le nom apparaît en début de document (le plus précis l'emporte). */
    private TypesContrat deviner(String texte) {
        String zone = ContratPdfParser.plier(texte.length() > 4000 ? texte.substring(0, 4000) : texte);
        return typesRepo.findByActifTrue().stream()
                .filter(t -> t.getNom() != null && t.getNom().strip().length() >= 4)
                .filter(t -> zone.contains(ContratPdfParser.plier(t.getNom().strip())))
                .max(Comparator.comparingInt(t -> t.getNom().strip().length()))
                .orElse(null);
    }
}
