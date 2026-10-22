package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Notifications;
import com.suivicontrats.front.entity.Utilisateurs;
import com.suivicontrats.front.repository.NotificationsRepository;
import com.suivicontrats.front.repository.UtilisateursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationsService {

    private final NotificationsRepository repo;
    private final UtilisateursRepository utilisateursRepo;

    public List<Notifications> all(){ requireAdmin(); return repo.findAll(); }
    public Notifications getOne(Long id){
        Notifications n = repo.findById(id).orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        if (!isAdmin() && !currentUserId().equals(n.getUtilisateurId())) throw new AccessDeniedException("Cette notification ne vous est pas destinée.");
        return n;
    }
    public List<Notifications> byUser(Integer userId){ requireSelfOrAdmin(userId); return repo.findByUtilisateurIdOrderByDateCreationDesc(userId); }
    public List<Notifications> nonLues(Integer userId){ requireSelfOrAdmin(userId); return repo.findByUtilisateurIdAndLuOrderByDateCreationDesc(userId, false); }
    public long countNonLues(Integer userId){ requireSelfOrAdmin(userId); return repo.countByUtilisateurIdAndLuFalse(userId); }
    public Notifications save(Notifications n){ requireAdmin(); n.setId(null); return repo.save(n); }

    /** Crée une notification métier pour un destinataire précis. */
    @Transactional
    public Notifications notifyUser(Integer userId, Integer contractId, String title, String message, String type) {
        if (userId == null) return null;
        return saveForUser(userId, contractId, title, message, type);
    }

    private Notifications saveForUser(Integer userId, Integer contractId, String title, String message, String type) {
        return repo.save(Notifications.builder()
                .utilisateurId(userId).contratId(contractId)
                .titre(truncate(title, 150)).message(truncate(message, 500))
                .type(type).lu(false).build());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public void notifyUsers(List<Utilisateurs> users, Integer contractId, String title, String message, String type) {
        if (users == null || users.isEmpty()) return;
        users.stream().map(Utilisateurs::getId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> saveForUser(id, contractId, title, message, type));
    }

    public Notifications update(Long id, Notifications details){
        requireAdmin();
        Notifications notification = getOne(id);
        notification.setUtilisateurId(details.getUtilisateurId());
        notification.setContratId(details.getContratId());
        notification.setParametreAlerteId(details.getParametreAlerteId());
        notification.setTitre(details.getTitre());
        notification.setMessage(details.getMessage());
        notification.setType(details.getType());
        notification.setLu(details.getLu());
        return repo.save(notification);
    }
    
    public Notifications marquerLu(Long id){
        Notifications n = getOne(id);
        n.setLu(true);
        return repo.save(n);
    }

    @Transactional
    public int marquerToutesLues(Integer userId) {
        requireSelfOrAdmin(userId);
        return repo.markAllRead(userId);
    }

    @Transactional
    public void notifyDeadline(Integer userId, Integer contractId, Long parameterId, String title, String message) {
        if (userId != null) saveDeadlineForUser(userId, contractId, parameterId, title, message);
    }

    private void saveDeadlineForUser(Integer userId, Integer contractId, Long parameterId, String title, String message) {
        if (repo.existsByUtilisateurIdAndContratIdAndParametreAlerteId(userId, contractId, parameterId)) return;
        try {
            repo.save(Notifications.builder().utilisateurId(userId).contratId(contractId)
                    .parametreAlerteId(parameterId).titre(truncate(title, 150)).message(truncate(message, 500))
                    .type("ECHEANCE").lu(false).build());
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            // Un autre nœud a pu enregistrer cette même alerte au même instant.
        }
    }

    public void delete(Long id){ getOne(id); repo.deleteById(id); }

    private Integer currentUserId(){
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!(auth.getPrincipal() instanceof Integer id)) throw new AccessDeniedException("Authentification requise");
        return id;
    }
    private boolean isAdmin(){ Authentication auth=SecurityContextHolder.getContext().getAuthentication(); return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")); }
    private void requireAdmin(){ if(!isAdmin()) throw new AccessDeniedException("Opération réservée à l'administrateur."); }
    private void requireSelfOrAdmin(Integer userId){ if(!isAdmin()&&!currentUserId().equals(userId)) throw new AccessDeniedException("Accès réservé à votre compte."); }
}
