package com.suivicontrats.front.entity;

import jakarta.persistence.*;
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

    @Column(name = "utilisateurs_secret_2fa", length = 255)
    private String secret2fa;

    @Column(name = "utilisateurs_enabled_2fa", nullable = false)
    @Builder.Default
    private Boolean enabled2fa = false;

    public enum Role {
        ADMIN,
        GESTIONNAIRE,
        VALIDEUR,
        JURIDIQUE
    }
}
