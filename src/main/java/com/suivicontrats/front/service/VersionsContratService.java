
package com.suivicontrats.front.service;

import com.suivicontrats.front.entity.VersionsContrat;
import com.suivicontrats.front.repository.VersionsContratRepository;

import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionsContratService {

    private final VersionsContratRepository repo;
    private final ContratService contratService;

    /**
     * Retourne les versions des contrats accessibles à l'utilisateur.
     */
    public List<VersionsContrat> all() {

        var allowed = contratService.getAll()
                .stream()
                .map(c -> c.getId())
                .collect(java.util.stream.Collectors.toSet());

        return repo.findAll()
                .stream()
                .filter(v -> allowed.contains(v.getContratId()))
                .toList();
    }

    /**
     * Retourne toutes les versions d'un contrat,
     * classées par numéro de version croissant.
     */
    public List<VersionsContrat> byContrat(Integer contratId) {

        contratService.getById(contratId);

        return repo.findByContratIdOrderByVersionAsc(contratId);
    }

    /**
     * Retourne une version précise.
     */
    public VersionsContrat getOne(Long id) {

        VersionsContrat version = repo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Version non trouvée")
                );

        contratService.getById(version.getContratId());

        return version;
    }

    /**
     * Interdit la création manuelle d'une version.
     *
     * Les versions sont créées automatiquement par ContratService
     * et AvenantsService.
     */
    public VersionsContrat save(VersionsContrat version) {

        throw new AccessDeniedException(
                "Les versions sont créées automatiquement lors de la création "
                + "ou de la modification d'un contrat, ou lors de la création "
                + "d'un avenant."
        );
    }

    /**
     * Une version archivée ne peut pas être modifiée.
     */
    public VersionsContrat update(Long id, VersionsContrat details) {

        getOne(id);

        throw new AccessDeniedException(
                "Une version archivée est immuable."
        );
    }

    /**
     * Une version archivée ne peut pas être supprimée.
     */
    public void delete(Long id) {

        getOne(id);

        throw new AccessDeniedException(
                "Une version archivée ne peut pas être supprimée."
        );
    }
}
