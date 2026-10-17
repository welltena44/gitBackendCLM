package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "regles_validation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReglesValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regles_validation_id")
    private Long id;

    @Column(name = "regles_validation_type_contrat_id")
    private Long typeContratId; // NULL = règle globale pour tous les types

    @Column(name = "regles_validation_montant_minimum", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantMinimum = BigDecimal.ZERO;

    @Column(name = "regles_validation_montant_maximum", precision = 15, scale = 2)
    private BigDecimal montantMaximum; // NULL = sans limite

    @Column(name = "regles_validation_niveau", nullable = false)
    private Integer niveau; // 1, 2, 3...

    @Column(name = "regles_validation_role_requis", nullable = false, length = 30)
    private String roleRequis; // ADMIN, GESTIONNAIRE, VALIDEUR, JURIDIQUE
}