package com.suivicontrats.front.dto;
import lombok.*;
@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor
public class LoginResponse {
    private Integer id;
    private Integer matricule;
    private String nom;
    private String prenom;
    private String email; 
    private String role;
    private String service;
    private Boolean actif; 
    private Boolean changementMotDePasseRequis;
    private String message;
    private String token;
}
