package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "obligations_contrat", indexes = @Index(name = "idx_obligations_contrat_statut", columnList = "obligations_contrat_contrat_id, obligations_contrat_statut"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObligationsContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "obligations_contrat_id")
    private Long id;

    @Column(name = "obligations_contrat_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "obligations_contrat_titre", nullable = false, length = 150)
    private String titre;

    @Column(name = "obligations_contrat_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "obligations_contrat_echeance")
    private LocalDate echeance;

    @Column(name = "obligations_contrat_statut", nullable = false, length = 20)
    @Builder.Default
    private String statut = "EN_COURS";

    @Column(name = "obligations_contrat_responsable_id")
    private Integer responsableId;

    @Column(name = "obligations_contrat_cree_par")
    private Integer creePar;

    @Column(name = "obligations_contrat_date_creation", nullable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "obligations_contrat_date_cloture")
    private LocalDateTime dateCloture;
}
