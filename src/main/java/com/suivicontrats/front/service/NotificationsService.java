package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.Notifications;
import com.suivicontrats.front.repository.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationsService {

    private final NotificationsRepository repo;

    public List<Notifications> all(){ return repo.findAll(); }
    public Notifications getOne(Long id){
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Notification non trouvée"));
    }
    public List<Notifications> byUser(Integer userId){ return repo.findByUtilisateurIdOrderByDateCreationDesc(userId); }
    public List<Notifications> nonLues(Integer userId){ return repo.findByUtilisateurIdAndLuOrderByDateCreationDesc(userId, false); }
    public long countNonLues(Integer userId){ return repo.countByUtilisateurIdAndLuFalse(userId); }
    public Notifications save(Notifications n){ n.setId(null); return repo.save(n); }

    public Notifications update(Long id, Notifications details){
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

    public void delete(Long id){ repo.deleteById(id); }
}
