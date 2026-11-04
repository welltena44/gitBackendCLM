package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "avenants", uniqueConstraints = @UniqueConstraint(name = "uq_avenant_numero", columnNames = {
        "avenants_contrat_id", "avenants_numero" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avenants {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "avenants_id")
    private Long id;

    @Column(name = "avenants_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "avenants_numero", nullable = false)
    private Integer numero;

    @Column(name = "avenants_description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "avenants_modifications", columnDefinition = "TEXT")
    private String modifications;

    @Column(name = "avenants_date_avenant", nullable = false)
    private LocalDate dateAvenant;

    @Column(name = "avenants_montant_avant", precision = 15, scale = 2)
    private BigDecimal montantAvant;

    @Column(name = "avenants_montant_apres", precision = 15, scale = 2)
    private BigDecimal montantApres;

    @Column(name = "avenants_date_fin_avant")
    private LocalDate dateFinAvant;

    @Column(name = "avenants_date_fin_apres")
    private LocalDate dateFinApres;

    @Column(name = "avenants_document_id")
    private Long documentId;

    @Column(name = "avenants_cree_par")
    private Integer creePar;

    // Statut indépendant du contrat : un avenant en attente ne modifie jamais le contrat actif.
    @Column(name = "avenants_statut", nullable = false, length = 30)
    @Builder.Default
    private String statut = "EN_ATTENTE";

    // Chaque nouvelle soumission après demande de modification ouvre un nouveau cycle.
    @Column(name = "avenants_cycle_validation", nullable = false)
    @Builder.Default
    private Integer cycleValidation = 1;

    @Column(name = "avenants_date_creation", nullable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
