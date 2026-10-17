package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = @Index(
        name = "idx_notifications_user",
        columnList = "notifications_utilisateur_id, notifications_lu"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notifications_id")
    private Long id;

    @Column(name = "notifications_utilisateur_id", nullable = false)
    private Integer utilisateurId;

    @Column(name = "notifications_contrat_id")
    private Integer contratId;

    @Column(name = "notifications_parametre_alerte_id")
    private Long parametreAlerteId;

    @Column(name = "notifications_titre", nullable = false, length = 150)
    private String titre;

    @Column(name = "notifications_message", nullable = false, length = 500)
    private String message;

    @Column(name = "notifications_type", nullable = false, length = 30)
    private String type; // ECHEANCE, RENOUVELLEMENT, RESILIATION, VALIDATION, AVENANT, INFO

    @Column(name = "notifications_lu", nullable = false)
    @Builder.Default
    private Boolean lu = false;

    @Column(name = "notifications_date_creation")
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
