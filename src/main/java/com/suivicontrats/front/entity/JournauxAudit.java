package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "journaux_audit", indexes = {
        @Index(name = "idx_audit_contract", columnList = "journaux_audit_contrat_id"),
        @Index(name = "idx_audit_date", columnList = "journaux_audit_date_action")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournauxAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journaux_audit_id")
    private Long id;

    @Column(name = "journaux_audit_utilisateur_id")
    private Integer utilisateurId;

    @Column(name = "journaux_audit_contrat_id")
    private Integer contratId;

    @Column(name = "journaux_audit_reference_contrat", length = 50)
    private String referenceContrat;

    @Column(name = "journaux_audit_action", nullable = false, length = 50)
    private String action;

    @Column(name = "journaux_audit_ancienne_valeur", columnDefinition = "TEXT")
    private String ancienneValeur;

    @Column(name = "journaux_audit_nouvelle_valeur", columnDefinition = "TEXT")
    private String nouvelleValeur;

    @Column(name = "journaux_audit_date_action")
    @Builder.Default
    private LocalDateTime dateAction = LocalDateTime.now();
}
