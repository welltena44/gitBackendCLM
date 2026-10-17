package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "versions_contrat", uniqueConstraints = @UniqueConstraint(
        name = "uq_version",
        columnNames = {"versions_contrat_contrat_id", "versions_contrat_version"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionsContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "versions_contrat_id")
    private Long id;

    @Column(name = "versions_contrat_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "versions_contrat_version", nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "versions_contrat_contenu", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> contenu;

    @Column(name = "versions_contrat_date_creation", nullable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "versions_contrat_cree_par")
    private Integer creePar;

    @Column(name = "versions_contrat_avenant_id")
    private Long avenantId;
}
