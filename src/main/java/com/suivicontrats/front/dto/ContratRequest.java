package com.suivicontrats.front.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContratRequest {
    private String reference;
    private String objet;
    private String description;
    private String clauses;
    private BigDecimal montant;
    private String devise;
    private LocalDate dateSignature;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer dureeMois;
    private String statut;
    private String service;
    private Boolean renouvellementAutomatique;
    private Integer joursPreavis;
    private Integer responsableId;
    private Long typeId;
    private Integer creePar;
}