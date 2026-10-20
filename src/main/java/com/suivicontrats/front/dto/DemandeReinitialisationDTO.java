package com.suivicontrats.front.dto;

import java.time.LocalDateTime;

public record DemandeReinitialisationDTO(
        Long id, Integer matricule, String nom, LocalDateTime dateCreation) {}
