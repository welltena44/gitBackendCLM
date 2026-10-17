package com.suivicontrats.front.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contrats", indexes = {
        @Index(name = "idx_contrats_statut", columnList = "contrats_statut"),
        @Index(name = "idx_contrats_date_fin", columnList = "contrats_date_fin"),
        @Index(name = "idx_contrats_type", columnList = "contrats_type_id"),
        @Index(name = "idx_contrats_service", columnList = "contrats_service"),
        @Index(name = "idx_contrats_responsable", columnList = "contrats_responsable_id"),
        @Index(name = "idx_contrats_dashboard", columnList = "contrats_type_id, contrats_statut, contrats_date_fin")
})
public class Contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contrats_id")
    private Integer id;

    @Column(name = "contrats_numero", nullable = false, unique = true, length = 50)
    private String reference;

    @Column(name = "contrats_objet", nullable = false, length = 255)
    private String objet;

    @Column(name = "contrats_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "contrats_clauses", columnDefinition = "TEXT")
    private String clauses;

    @Column(name = "contrats_montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "contrats_devise", nullable = false, length = 3)
    private String devise = "Ar";

    @Column(name = "contrats_date_signature")
    private LocalDate dateSignature;

    @Column(name = "contrats_date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "contrats_date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "contrats_duree_mois")
    private Integer dureeMois;

    @Column(name = "contrats_statut", nullable = false, length = 20)
    private String statut = "BROUILLON";

    @Column(name = "contrats_service", length = 100)
    private String service;

    @Column(name = "contrats_renouvellement_automatique", nullable = false)
    private Boolean renouvellementAutomatique = false;

    @Column(name = "contrats_jours_preavis", nullable = false)
    private Integer joursPreavis = 0;

    @Column(name = "contrats_responsable_id", nullable = false)
    private Integer responsableId;

    @Column(name = "contrats_type_id", nullable = false)
    private Long typeId;

    @Column(name = "contrats_cree_par")
    private Integer creePar;

    @Column(name = "contrats_date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "contrats_date_modification", nullable = false)
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    public Contrat() {}

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClauses() { return clauses; }
    public void setClauses(String clauses) { this.clauses = clauses; }

    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }

    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }

    public LocalDate getDateSignature() { return dateSignature; }
    public void setDateSignature(LocalDate dateSignature) { this.dateSignature = dateSignature; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public Integer getResponsableId() { return responsableId; }
    public void setResponsableId(Integer responsableId) { this.responsableId = responsableId; }

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }

    public Integer getDureeMois() { return dureeMois; }
    public void setDureeMois(Integer dureeMois) { this.dureeMois = dureeMois; }

    public Boolean getRenouvellementAutomatique() { return renouvellementAutomatique; }
    public void setRenouvellementAutomatique(Boolean renouvellementAutomatique) { this.renouvellementAutomatique = renouvellementAutomatique; }

    public Integer getJoursPreavis() { return joursPreavis; }
    public void setJoursPreavis(Integer joursPreavis) { this.joursPreavis = joursPreavis; }

    public Integer getCreePar() { return creePar; }
    public void setCreePar(Integer creePar) { this.creePar = creePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
}
