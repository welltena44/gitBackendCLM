package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "types_contrat")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypesContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "types_contrat_id")
    private Long id;

    @Column(name = "types_contrat_nom", nullable = false, unique = true, length = 100)
    private String nom;

    @Column(name = "types_contrat_description", length = 255)
    private String description;

    @Column(name = "types_contrat_actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}