package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateurs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "utilisateurs_id")
    private Integer id;

    @Column(name = "utilisateurs_matricule", nullable = false, unique = true)
    private Integer matricule;

    @Column(name = "utilisateurs_nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "utilisateurs_prenom", nullable = false, length = 100)
    private String prenom;

    @Column(name = "utilisateurs_email", nullable = false, unique = true, length = 150)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "utilisateurs_mot_de_passe", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(name = "utilisateurs_role", nullable = false, length = 30)
    private Role role;

    @Column(name = "utilisateurs_service", length = 100)
    private String service;

    @Column(name = "utilisateurs_actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @CreationTimestamp
    @Column(name = "utilisateurs_date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "utilisateurs_tentatives_echec", nullable = false)
    @Builder.Default
    private Integer tentativesEchec = 0;

    @Column(name = "utilisateurs_verrouille_jusqua")
    private LocalDateTime verrouilleJusqua;

    @Column(name = "utilisateurs_changement_mot_de_passe_requis", nullable = false)
    @Builder.Default
    private Boolean changementMotDePasseRequis = false;

    public enum Role {
        ADMIN,
        GESTIONNAIRE,
        VALIDEUR,
        JURIDIQUE
    }
}
