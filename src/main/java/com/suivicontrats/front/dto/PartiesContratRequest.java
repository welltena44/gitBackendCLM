package com.suivicontrats.front.dto;
import lombok.Data;

@Data
public class PartiesContratRequest {
    private Integer contratId;
    private String nom;
    private String type; // FOURNISSEUR, CLIENT...
    private String email;
    private String telephone;
}