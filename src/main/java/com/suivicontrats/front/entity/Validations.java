package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "validations", indexes = {
        @Index(name = "idx_validations_contract", columnList = "validations_contrat_id"),
        @Index(name = "idx_validations_decision", columnList = "validations_decision")
}, uniqueConstraints = @UniqueConstraint(
        name = "uq_validation_cycle_niveau",
        columnNames = {"validations_contrat_id", "validations_cycle", "validations_niveau"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Validations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "validations_id")
    private Long id;

    @Column(name = "validations_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "validations_valideur_id")
    private Integer valideurId;

    @Column(name = "validations_demandeur_id")
    private Integer demandeurId;

    @Column(name = "validations_niveau", nullable = false)
    private Integer niveau;

    @Column(name = "validations_role_requis", nullable = false, length = 30)
    private String roleRequis;

    @Column(name = "validations_cycle", nullable = false)
    @Builder.Default
    private Integer cycle = 1;

    @Column(name = "validations_decision", nullable = false, length = 30)
    @Builder.Default
    private String decision = "EN_ATTENTE"; // EN_ATTENTE, APPROUVE, REJETE, MODIFICATION_DEMANDEE

    @Column(name = "validations_commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "validations_date_soumission", nullable = false)
    @Builder.Default
    private LocalDateTime dateSoumission = LocalDateTime.now();

    @Column(name = "validations_date_validation")
    private LocalDateTime dateValidation;
}
