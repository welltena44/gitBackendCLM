package com.suivicontrats.front.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex) {
        log.warn("Contrainte de base de données violée", ex);
        String detail = (ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage())).toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("unique") || detail.contains("duplicate") || detail.contains("already exists"))
            return response(HttpStatus.CONFLICT, "Cette valeur existe déjà. Vérifiez les matricules, adresses e-mail, noms et délais déjà enregistrés.");
        if (detail.contains("foreign key") || detail.contains("violates foreign key") || detail.contains("constraint"))
            return response(HttpStatus.CONFLICT, "Cette opération est impossible : l'élément est encore utilisé par d'autres données. Désactivez-le plutôt que de le supprimer.");
        return response(HttpStatus.CONFLICT, "L'opération viole une contrainte de cohérence des données.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> database(DataAccessException ex) {
        log.error("Erreur d'accès à la base de données", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue lors du traitement des données.");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> locked(LockedException ex) {
        return response(HttpStatus.LOCKED, safeMessage(ex.getMessage(), "Compte temporairement verrouillé."));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> security(SecurityException ex) {
        return response(HttpStatus.FORBIDDEN, safeMessage(ex.getMessage(), "Accès interdit."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN,
                safeMessage(ex.getMessage(), "Vous n'avez pas les droits nécessaires pour cette opération."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage())
                .orElse("Les données envoyées ne sont pas valides.");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, safeMessage(ex.getMessage(), "Requête invalide."));
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, Object>> mail(MailException ex) {
        log.warn("Échec de l'envoi d'un courriel", ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                "Le courriel de notification n'a pas pu être envoyé.");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> runtime(RuntimeException ex) {
        String raw = ex.getMessage() == null ? "" : ex.getMessage();
        String message = raw.toLowerCase(java.util.Locale.ROOT);
        if (message.contains("non trouvé") || message.contains("non trouvée")) {
            return response(HttpStatus.NOT_FOUND, "La ressource demandée est introuvable.");
        }
        if (message.contains("référence déjà existante")) {
            return response(HttpStatus.CONFLICT, "Cette référence de contrat existe déjà.");
        }
        // Toute erreur inattendue est journalisée côté serveur, jamais renvoyée au client.
        log.error("Erreur interne non traitée", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue.");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
