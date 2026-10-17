package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parametres_alertes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametresAlertes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parametres_alertes_id")
    private Long id;

    @Column(name = "parametres_alertes_jours_avant", nullable = false, unique = true)
    private Integer joursAvant;

    @Column(name = "parametres_alertes_actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}