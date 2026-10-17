package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parties_contrat", indexes = @Index(
        name = "idx_parties_contrat_id",
        columnList = "parties_contrat_contrat_id"))
public class PartiesContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parties_contrat_id")
    private Long id;

    @Column(name = "parties_contrat_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "parties_contrat_nom", nullable = false, length = 150)
    private String nom;

    @Column(name = "parties_contrat_type", length = 50)
    private String type;

    @Column(name = "parties_contrat_email", length = 150)
    private String email;

    @Column(name = "parties_contrat_telephone", length = 30)
    private String telephone;

}
