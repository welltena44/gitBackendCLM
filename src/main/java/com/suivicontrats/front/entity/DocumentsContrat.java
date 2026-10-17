package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents_contrat", indexes = @Index(
        name = "idx_documents_contrat_contrat",
        columnList = "documents_contrat_contrat_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentsContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "documents_contrat_id")
    private Long id;

    @Column(name = "documents_contrat_contrat_id", nullable = false)
    private Integer contratId;

    @Column(name = "documents_contrat_nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "documents_contrat_chemin", nullable = false, length = 500)
    private String chemin;

    @Column(name = "documents_contrat_type", length = 50)
    private String type; // pdf, docx, image...

    @Column(name = "documents_contrat_version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "documents_contrat_importe_par")
    private Integer importePar;

    @Column(name = "documents_contrat_date_import", nullable = false)
    private LocalDateTime dateImport;

    @PrePersist
    public void prePersist(){
        if(dateImport == null) dateImport = LocalDateTime.now();
        if(version == null) version = 1;
    }
}
