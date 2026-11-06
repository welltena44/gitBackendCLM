package com.suivicontrats.front.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait les informations d'un contrat à partir du texte brut d'un PDF.
 * Classe sans dépendance : elle ne fait que de l'analyse de texte (heuristiques adaptées aux
 * contrats rédigés en français). Elle n'invente jamais de valeur : un champ introuvable reste null.
 */
public final class ContratPdfParser {

    private ContratPdfParser() {
    }

    public record Extraction(
            String reference,
            String objet,
            String description,
            BigDecimal montant,
            String devise,
            String dateDebut,
            String dateFin,
            Integer dureeMois,
            Integer joursPreavis,
            Boolean renouvellementAutomatique,
            String clauses,
            List<String> champsDetectes,
            List<String> avertissements) {
    }

    private static final Map<String, Integer> MOIS = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("mars", 3), Map.entry("avril", 4),
            Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7), Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11),
            Map.entry("decembre", 12));

    private static final Map<String, Integer> NOMBRES = Map.ofEntries(
            Map.entry("un", 1), Map.entry("une", 1), Map.entry("deux", 2), Map.entry("trois", 3),
            Map.entry("quatre", 4), Map.entry("cinq", 5), Map.entry("six", 6), Map.entry("sept", 7),
            Map.entry("huit", 8), Map.entry("neuf", 9), Map.entry("dix", 10), Map.entry("quinze", 15),
            Map.entry("vingt", 20), Map.entry("trente", 30), Map.entry("quarante", 40),
            Map.entry("cinquante", 50), Map.entry("soixante", 60), Map.entry("cent", 100));

    private static final Pattern DATE_NUM = Pattern.compile("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})(?!\\d)");
    private static final Pattern DATE_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern DATE_TXT = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*(?:er)?\\s+(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre)\\s+(\\d{4})");

    private static final String[] MOTS_DEBUT = { "prend effet", "prendra effet", "entre en vigueur",
            "entrera en vigueur", "a compter du", "a compter de", "date de debut", "date d'effet", "debute le",
            "debutera le", "commence le", "commencera le", "effet le", "debut :", "debut le", "demarre le" };
    private static final String[] MOTS_FIN = { "echeance", "expire", "expirera", "prend fin", "prendra fin",
            "se termine", "se terminera", "jusqu'au", "jusqu'a", "date de fin", "terme le", "au plus tard le",
            "fin le", "fin :", "arrive a" };

    public static Extraction parse(String brut) {
        List<String> detectes = new ArrayList<>();
        List<String> avert = new ArrayList<>();
        if (brut == null || brut.isBlank())
            return new Extraction(null, null, null, null, null, null, null, null, null, null, null, detectes,
                    List.of("Aucun texte à analyser."));

        String orig = nettoyer(brut);
        String fold = plier(orig);
        String plat = orig.replaceAll("\\s*\\n\\s*", " ");
        String platFold = plier(plat);

        String reference = trouverReference(orig);
        String[] objet = trouverObjet(orig, plat, platFold);
        MontantTrouve montant = trouverMontant(plat, platFold, avert);
        LocalDate[] dates = trouverDates(platFold, plat);
        Integer duree = trouverDuree(platFold);
        Integer preavis = trouverPreavis(platFold);
        Boolean renouvellement = trouverRenouvellement(platFold);
        String clauses = trouverClauses(orig, fold);

        LocalDate debut = dates[0], fin = dates[1];
        if (duree == null && debut != null && fin != null) {
            long mois = ChronoUnit.MONTHS.between(debut, fin.plusDays(1));
            if (mois > 0) {
                duree = (int) mois;
                avert.add("La durée a été calculée à partir des dates (" + mois + " mois).");
            }
        }
        if (fin == null && debut != null && duree != null) {
            fin = debut.plusMonths(duree).minusDays(1);
            avert.add("La date de fin a été calculée à partir du début et de la durée : à vérifier.");
        }
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            avert.add("La date de fin extraite n'est pas postérieure à la date de début : vérifiez les dates.");
        }

        marquer(detectes, avert, "référence", reference);
        marquer(detectes, avert, "objet", objet[0]);
        marquer(detectes, avert, "montant", montant == null ? null : montant.valeur);
        marquer(detectes, avert, "date de début", debut);
        marquer(detectes, avert, "date de fin", fin);
        if (duree != null) detectes.add("durée");
        if (preavis != null) detectes.add("préavis");
        if (renouvellement != null) detectes.add("renouvellement");
        if (clauses != null) detectes.add("clauses");

        return new Extraction(reference, objet[0], objet[1],
                montant == null ? null : montant.valeur, montant == null ? null : montant.devise,
                debut == null ? null : debut.toString(), fin == null ? null : fin.toString(),
                duree, preavis, renouvellement, clauses, detectes, avert);
    }

    // ------------------------------------------------------------------ utilitaires

    /** Version sans accents et en minuscules, de même longueur que l'original. */
    public static String plier(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char base = d.isEmpty() ? c : d.charAt(0);
            if (c == '’' || c == '‘' || c == '`') base = '\'';
            sb.append(Character.toLowerCase(base));
        }
        return sb.toString();
    }

    private static String nettoyer(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFC);
        t = t.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ').replace("\r", "");
        t = t.replace('\t', ' ').replace('’', '\'');
        t = t.replaceAll("[ ]{2,}", " ");
        t = t.replaceAll("(?m)^[ ]+|[ ]+$", "");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.strip();
    }

    private static void marquer(List<String> detectes, List<String> avert, String nom, Object valeur) {
        if (valeur == null) avert.add("Champ non détecté : " + nom + " (à saisir manuellement).");
        else detectes.add(nom);
    }

    // ------------------------------------------------------------------ référence

    private static final Pattern REF_LABEL = Pattern.compile(
            "(?i)(?:r[ée]f[ée]rence|r[ée]f\\.?|n°|n\\u00BA|num[ée]ro|n\\.\\s*°|contrat\\s+n°?|no\\.?)\\s*(?:du\\s+contrat\\s*)?[:.\\-–]?\\s*([A-Za-z0-9][A-Za-z0-9/_.\\-]{3,40})");
    private static final Pattern REF_CODE = Pattern.compile(
            "\\b([A-Z]{2,8}[-/][A-Z0-9]{2,12}(?:[-/][A-Z0-9]{1,12})+)\\b");

    private static String trouverReference(String orig) {
        Matcher m = REF_LABEL.matcher(orig);
        while (m.find()) {
            String tok = nettoyerFinToken(m.group(1));
            if (refValide(tok)) return tok.toUpperCase(Locale.ROOT);
        }
        String debut = orig.length() > 2000 ? orig.substring(0, 2000) : orig;
        m = REF_CODE.matcher(debut);
        while (m.find()) {
            String tok = nettoyerFinToken(m.group(1));
            if (refValide(tok)) return tok;
        }
        return null;
    }

    private static String nettoyerFinToken(String t) {
        return t.replaceAll("[.,;:\\-/]+$", "");
    }

    private static boolean refValide(String t) {
        if (t == null || t.length() < 4 || t.length() > 50) return false;
        if (!t.matches(".*\\d.*")) return false;
        if (t.matches("\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}")) return false;
        return !t.matches("\\d{9,}"); // suites de chiffres (NIF, téléphone) : pas une référence
    }

    // ------------------------------------------------------------------ objet

    private static String[] trouverObjet(String orig, String plat, String platFold) {
        // 1) « Objet : ... »
        Matcher m = Pattern.compile("(?im)^\\s*objet(?:\\s+du\\s+contrat)?\\s*[:：]\\s*(.+)$").matcher(orig);
        if (m.find()) {
            String phrase = m.group(1).strip();
            return new String[] { coupeObjet(phrase), borne(phrase, 2000) };
        }
        // 2) « a pour objet ... »
        m = Pattern.compile("(?is)\\bont\\s+pour\\s+objet\\b|\\ba\\s+pour\\s+objet\\b").matcher(platFold);
        if (m.find()) {
            int debut = m.end();
            int fin = finPhrase(plat, debut);
            String phrase = plat.substring(debut, fin).strip();
            if (phrase.length() >= 8) return new String[] { coupeObjet(phrase), borne(phrase, 2000) };
        }
        // 3) première ligne de titre contenant « contrat »
        String[] lignes = orig.split("\n");
        int vues = 0;
        for (String l : lignes) {
            String t = l.strip();
            if (t.isEmpty()) continue;
            if (++vues > 12) break;
            String f = plier(t);
            if (f.matches(".*\\b(contrat|convention|marche|accord|bail|protocole)\\b.*") && t.length() <= 200) {
                String titre = t.equals(t.toUpperCase(Locale.ROOT)) ? capitaliser(t.toLowerCase(Locale.ROOT)) : t;
                return new String[] { borne(titre, 255), null };
            }
        }
        return new String[] { null, null };
    }

    private static int finPhrase(String s, int debut) {
        int i = debut;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '.' && (i + 1 >= s.length() || s.charAt(i + 1) == ' ')
                    && !(i >= 2 && Character.isLetter(s.charAt(i - 1)) && (i < 3 || s.charAt(i - 2) == ' ')))
                break;
            i++;
        }
        return Math.min(i, Math.min(s.length(), debut + 2000));
    }

    private static String coupeObjet(String phrase) {
        String p = phrase.strip();
        Matcher m = Pattern.compile("(?i),?\\s+(incluant|comprenant|notamment|y compris|ainsi que|;)").matcher(p);
        if (p.length() > 120 && m.find() && m.start() > 25) p = p.substring(0, m.start());
        p = p.replaceFirst("(?i)^(?:de\\s+la\\s+|de\\s+l'|du\\s+|des\\s+|d'|la\\s+|le\\s+|les\\s+|l')", "");
        return borne(capitaliser(p.strip()), 255);
    }

    private static String capitaliser(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String borne(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max);
        return t.substring(0, cut > max / 2 ? cut : max).strip();
    }

    // ------------------------------------------------------------------ montant

    private static final class MontantTrouve {
        BigDecimal valeur;
        String devise;
        int score;
    }

    private static final Pattern MONTANT = Pattern.compile(
            "(?<![\\d.,])(\\d{1,3}(?:[ .,']\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)(?![\\d])"
                    + "(\\s*(?:ariarys?|ar\\b|mga|euros?|eur\\b|€|usd|\\$|dollars?))?");
    private static final String[] MOTS_MONTANT = { "montant", "prix", "total", "forfait", "s'eleve", "somme",
            "remuneration", "honoraires", "cout", "valeur du contrat", "budget", "contrepartie" };
    private static final String[] MOTS_PENALISANTS = { "penalite", "pour mille", "interet", "taux", "acompte",
            "garantie", "caution", "tva", "par mois", "mensuel", "par trimestre", "par an", "limite" };

    private static MontantTrouve trouverMontant(String plat, String platFold, List<String> avert) {
        Matcher m = MONTANT.matcher(platFold);
        MontantTrouve meilleur = null;
        java.util.Set<BigDecimal> distincts = new java.util.HashSet<>();
        while (m.find()) {
            String nombre = m.group(1);
            String suffixe = m.group(2);
            // exclusions : pourcentages, pour mille, dates, années
            String apres = platFold.substring(m.end(1), Math.min(platFold.length(), m.end(1) + 3)).strip();
            if (apres.startsWith("%") || apres.startsWith("\u2030") || apres.startsWith("‰")) continue;
            BigDecimal v = lireNombre(nombre);
            if (v == null || v.signum() <= 0) continue;
            // éléments de dates (13/12/2025, 2025-12-13) : jamais des montants
            char av = m.start(1) > 0 ? platFold.charAt(m.start(1) - 1) : ' ';
            char ap = m.end(1) < platFold.length() ? platFold.charAt(m.end(1)) : ' ';
            if (av == '/' || av == '-' || ap == '/' || (ap == '-' && m.end(1) + 1 < platFold.length()
                    && Character.isDigit(platFold.charAt(m.end(1) + 1))))
                continue;
            String devise = devise(suffixe);
            if (devise == null) { // devise avant le nombre ?
                String avant = platFold.substring(Math.max(0, m.start(1) - 6), m.start(1));
                if (avant.matches(".*(?:\\bar|mga|eur|usd|€|\\$)\\s*$")) devise = devise(avant.replaceAll("^.*?(ar|mga|eur|usd|€|\\$)\\s*$", "$1"));
            }
            String fenetre = platFold.substring(Math.max(0, m.start(1) - 90), m.start(1));
            int score = 0;
            if (devise != null) score += 3;
            for (String k : MOTS_MONTANT) if (fenetre.contains(k)) { score += 3; break; }
            for (String k : MOTS_PENALISANTS) if (fenetre.substring(Math.max(0, fenetre.length() - 50)).contains(k)) { score -= 3; break; }
            if (score < 3) continue;
            if (devise == null && v.compareTo(BigDecimal.valueOf(100)) < 0) continue;
            // une année isolée (ex. « 2025 ») n'est pas un montant
            if (devise == null && !nombre.matches(".*[ .,'].*") && v.compareTo(BigDecimal.valueOf(1900)) >= 0
                    && v.compareTo(BigDecimal.valueOf(2100)) <= 0)
                continue;
            distincts.add(v.stripTrailingZeros());
            if (meilleur == null || score > meilleur.score
                    || (score == meilleur.score && v.compareTo(meilleur.valeur) > 0)) {
                MontantTrouve t = new MontantTrouve();
                t.valeur = v;
                t.devise = devise;
                t.score = score;
                meilleur = t;
            }
        }
        if (distincts.size() > 1) avert.add("Plusieurs montants ont été repérés : vérifiez que le montant total est le bon.");
        return meilleur;
    }

    private static String devise(String s) {
        if (s == null) return null;
        String f = s.strip().toLowerCase(Locale.ROOT);
        if (f.startsWith("ar") || f.equals("mga")) return "Ar";
        if (f.startsWith("eur") || f.equals("€")) return "EUR";
        if (f.startsWith("usd") || f.equals("$") || f.startsWith("dollar")) return "USD";
        return null;
    }

    static BigDecimal lireNombre(String brut) {
        String s = brut.replace(" ", "").replace("'", "");
        int lastDot = s.lastIndexOf('.'), lastComma = s.lastIndexOf(',');
        try {
            if (lastDot >= 0 && lastComma >= 0) {
                char dec = lastDot > lastComma ? '.' : ',';
                char mil = dec == '.' ? ',' : '.';
                return new BigDecimal(s.replace(String.valueOf(mil), "").replace(dec, '.'));
            }
            char sep = lastDot >= 0 ? '.' : (lastComma >= 0 ? ',' : 0);
            if (sep == 0) return new BigDecimal(s);
            int occurrences = (int) s.chars().filter(c -> c == sep).count();
            String apres = s.substring(s.lastIndexOf(sep) + 1);
            if (occurrences > 1 || apres.length() == 3) return new BigDecimal(s.replace(String.valueOf(sep), ""));
            return new BigDecimal(s.replace(sep, '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ dates

    private record DateTrouvee(LocalDate date, int debut, int fin) {
    }

    private static LocalDate[] trouverDates(String platFold, String plat) {
        List<DateTrouvee> toutes = new ArrayList<>();
        Matcher m = DATE_NUM.matcher(platFold);
        while (m.find()) ajouter(toutes, m, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        m = DATE_ISO.matcher(platFold);
        while (m.find()) ajouter(toutes, m, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        m = DATE_TXT.matcher(platFold);
        while (m.find()) ajouter(toutes, m, Integer.parseInt(m.group(3)), MOIS.get(m.group(2)), Integer.parseInt(m.group(1)));
        toutes.sort((a, b) -> Integer.compare(a.debut, b.debut));

        LocalDate debut = null, fin = null;
        int precedentFin = 0;
        for (DateTrouvee d : toutes) {
            String fenetre = platFold.substring(Math.max(precedentFin, Math.max(0, d.debut - 90)), d.debut);
            int pDebut = dernierIndex(fenetre, MOTS_DEBUT), pFin = dernierIndex(fenetre, MOTS_FIN);
            if (pDebut > pFin && debut == null) debut = d.date;
            else if (pFin > pDebut && fin == null && (debut == null || d.date.isAfter(debut))) fin = d.date;
            precedentFin = d.fin;
        }
        // « du X au Y »
        if (debut == null || fin == null) {
            for (int i = 0; i + 1 < toutes.size(); i++) {
                DateTrouvee a = toutes.get(i), b = toutes.get(i + 1);
                String entre = platFold.substring(a.fin, b.debut).strip();
                String avant = platFold.substring(Math.max(0, a.debut - 25), a.debut);
                if (entre.matches("(?:au|a|jusqu'au|jusqu'a|et le|-|–)") && avant.matches(".*\\b(?:du|de|entre le|periode)\\s*$")
                        && b.date.isAfter(a.date)) {
                    if (debut == null) debut = a.date;
                    if (fin == null) fin = b.date;
                    break;
                }
            }
        }
        return new LocalDate[] { debut, fin };
    }

    private static void ajouter(List<DateTrouvee> liste, Matcher m, int an, int mois, int jour) {
        try {
            if (an < 1990 || an > 2100) return;
            liste.add(new DateTrouvee(LocalDate.of(an, mois, jour), m.start(), m.end()));
        } catch (RuntimeException e) {
            // date invalide (ex. 31/02) : ignorée
        }
    }

    private static int dernierIndex(String texte, String[] mots) {
        int best = -1;
        for (String k : mots) best = Math.max(best, texte.lastIndexOf(k));
        return best;
    }

    // ------------------------------------------------------------------ durée, préavis, renouvellement

    private static Integer trouverDuree(String f) {
        Matcher m = Pattern.compile("duree[^.;]{0,70}?(\\d{1,3})\\s*\\)?\\s*(mois|ans?|annees?)").matcher(f);
        if (m.find()) return enMois(Integer.parseInt(m.group(1)), m.group(2));
        m = Pattern.compile("(?:pour une duree de|pendant)\\s+(?:[a-z\\-]+\\s+)?\\(?(\\d{1,3})\\)?\\s*(mois|ans?|annees?)").matcher(f);
        if (m.find()) return enMois(Integer.parseInt(m.group(1)), m.group(2));
        return null;
    }

    private static Integer enMois(int n, String unite) {
        return unite.startsWith("an") ? n * 12 : n;
    }

    private static Integer trouverPreavis(String f) {
        Matcher m = Pattern.compile("preavis[^.;\\d]{0,50}?(\\d{1,3})\\s*\\)?\\s*(jours?|mois|semaines?)").matcher(f);
        if (m.find()) return enJours(Integer.parseInt(m.group(1)), m.group(2));
        m = Pattern.compile("(\\d{1,3})\\s*(jours?|mois|semaines?)\\s*(?:calendaires\\s*|francs\\s*|ouvres\\s*)?(?:de|d')\\s*preavis").matcher(f);
        if (m.find()) return enJours(Integer.parseInt(m.group(1)), m.group(2));
        m = Pattern.compile("preavis\\s+(?:de\\s+|d'une\\s+duree\\s+de\\s+)?([a-z]+(?:-[a-z]+)?)\\s+(jours?|mois|semaines?)").matcher(f);
        if (m.find() && NOMBRES.containsKey(m.group(1))) return enJours(NOMBRES.get(m.group(1)), m.group(2));
        return null;
    }

    private static Integer enJours(int n, String unite) {
        if (unite.startsWith("mois")) return n * 30;
        if (unite.startsWith("semaine")) return n * 7;
        return n;
    }

    private static Boolean trouverRenouvellement(String f) {
        if (Pattern.compile("sans\\s+(?:tacite\\s+)?(?:reconduction|renouvellement)|ne\\s+(?:sera|peut|pourra)\\s+pas\\s+(?:etre\\s+)?(?:renouvele|reconduit|proroge)|non\\s+(?:renouvelable|reconductible)|n'est\\s+pas\\s+(?:renouvelable|reconductible)|ne\\s+se\\s+renouvelle\\s+pas|pas\\s+de\\s+(?:tacite\\s+)?(?:reconduction|renouvellement)|aucun(?:e)?\\s+(?:tacite\\s+)?(?:reconduction|renouvellement)").matcher(f).find())
            return Boolean.FALSE;
        if (Pattern.compile("(?:renouvel|reconduc|reconduit)[^.]{0,80}?(?:automati|tacit)|(?:automati|tacit)[^.]{0,80}?(?:renouvel|reconduc|reconduit)").matcher(f).find())
            return Boolean.TRUE;
        return null;
    }

    // ------------------------------------------------------------------ clauses

    private static String trouverClauses(String orig, String fold) {
        Matcher m = Pattern.compile("(?m)^\\s*(?:article\\s+(?:1|premier|1er)\\b|titre\\s+i\\b)").matcher(fold);
        if (!m.find()) return null;
        int debut = m.start();
        int fin = orig.length();
        Matcher f = Pattern.compile("(?im)^\\s*(?:fait\\s+(?:a|en)\\b|signatures?\\b|lu\\s+et\\s+approuve)").matcher(fold);
        while (f.find()) if (f.start() > debut) { fin = f.start(); break; }
        String t = orig.substring(debut, fin).strip();
        if (t.length() < 40) return null;
        return t.length() > 20000 ? t.substring(0, 20000) : t;
    }
}
