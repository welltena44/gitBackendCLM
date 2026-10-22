-- =====================================================================
-- CLM - Initialisation complète d'une base PostgreSQL neuve
-- ATTENTION : ce script supprime les tables CLM existantes et leurs données.
-- Le schéma a été comparé au dump suiviContrats.sql : toutes ses tables,
-- colonnes, index et le déclencheur de modification sont représentés ici.
-- Seuls les comptes utilisateurs du dump sont conservés comme données;
-- les contrats, validations, documents et autres données métier sont omis.
-- Les migrations DBMigrationDemandesReinitialisation.sql et
-- DBMigrationCircuitsEcheances.sql sont intégrées ci-dessous ; elles ne sont
-- pas nécessaires après l'exécution de ce script sur une base neuve.
-- Pour mettre à niveau une base existante sans la réinitialiser, exécuter
-- DBMigrationWorkflowSignature.sql à la place de ce script.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
SET search_path TO public;

DROP TABLE IF EXISTS demandes_reinitialisation_mot_de_passe, journaux_audit, notifications, versions_contrat, avenants,
    validations, regles_validation, documents_contrat, parties_contrat,
    contrats, types_contrat, parametres_alertes, utilisateurs CASCADE;

-- ---------------------------------------------------------------------
-- UTILISATEURS
-- ---------------------------------------------------------------------
CREATE TABLE utilisateurs (
    utilisateurs_id             SERIAL       PRIMARY KEY,
    utilisateurs_matricule      INTEGER      NOT NULL UNIQUE CHECK (utilisateurs_matricule BETWEEN 1000000 AND 9999999),
    utilisateurs_nom            VARCHAR(100) NOT NULL,
    utilisateurs_prenom         VARCHAR(100) NOT NULL,
    utilisateurs_email          VARCHAR(150) NOT NULL UNIQUE,
    utilisateurs_mot_de_passe   VARCHAR(255) NOT NULL,
    utilisateurs_role           VARCHAR(30)  NOT NULL
                CHECK (utilisateurs_role IN ('ADMIN', 'GESTIONNAIRE', 'VALIDEUR', 'JURIDIQUE')),
    utilisateurs_service        VARCHAR(100),
    utilisateurs_actif          BOOLEAN      NOT NULL DEFAULT TRUE,
    utilisateurs_secret_2fa     VARCHAR(255),
    utilisateurs_enabled_2fa    BOOLEAN      NOT NULL DEFAULT FALSE,
    utilisateurs_changement_mot_de_passe_requis BOOLEAN NOT NULL DEFAULT FALSE,
    utilisateurs_date_creation  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Demandes de réinitialisation du mot de passe (migration intégrée).
CREATE TABLE IF NOT EXISTS demandes_reinitialisation_mot_de_passe (
    demande_id BIGSERIAL PRIMARY KEY,
    utilisateur_id INTEGER NOT NULL REFERENCES utilisateurs(utilisateurs_id) ON DELETE CASCADE,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
        CHECK (statut IN ('EN_ATTENTE', 'APPROUVEE', 'REJETEE')),
    date_creation TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_demandes_reinitialisation_statut_date
    ON demandes_reinitialisation_mot_de_passe (statut, date_creation);

-- ---------------------------------------------------------------------
-- TYPES DE CONTRATS
-- ---------------------------------------------------------------------
CREATE TABLE types_contrat (
    types_contrat_id           BIGSERIAL PRIMARY KEY,
    types_contrat_nom          VARCHAR(100) NOT NULL UNIQUE,
    types_contrat_description  VARCHAR(255),
    types_contrat_actif        BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- CONTRATS
-- ---------------------------------------------------------------------
CREATE TABLE contrats (
    contrats_id                          SERIAL        PRIMARY KEY,
    contrats_numero                      VARCHAR(50)   NOT NULL UNIQUE,
    contrats_objet                       VARCHAR(255)  NOT NULL,
    contrats_description                 TEXT,
    contrats_clauses                     TEXT,
    contrats_montant                     NUMERIC(15,2) NOT NULL CHECK (contrats_montant > 0),
    contrats_devise                      VARCHAR(3)    NOT NULL DEFAULT 'Ar',
    contrats_date_signature              DATE,
    contrats_date_debut                  DATE          NOT NULL,
    contrats_date_fin                    DATE          NOT NULL,
    contrats_duree_mois                  INTEGER,
    contrats_statut                      VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON'
                         CHECK (contrats_statut IN ('BROUILLON', 'EN_VALIDATION', 'SIGNE',
                                                    'ACTIF', 'EXPIRE', 'RESILIE', 'EN_ATTENTE',
                                                    'APPROUVE', 'REJETE')),
    contrats_service                     VARCHAR(100),
    contrats_renouvellement_automatique  BOOLEAN       NOT NULL DEFAULT FALSE,
    contrats_jours_preavis               INTEGER       NOT NULL DEFAULT 0 CHECK (contrats_jours_preavis >= 0),
    contrats_responsable_id              INTEGER       NOT NULL REFERENCES utilisateurs(utilisateurs_id),
    contrats_type_id                     BIGINT        NOT NULL REFERENCES types_contrat(types_contrat_id),
    contrats_cree_par                    INTEGER       REFERENCES utilisateurs(utilisateurs_id),
    contrats_date_creation               TIMESTAMP     NOT NULL DEFAULT NOW(),
    contrats_date_modification           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dates CHECK (contrats_date_fin > contrats_date_debut)
);

CREATE INDEX idx_contrats_statut      ON contrats(contrats_statut);
CREATE INDEX idx_contrats_date_fin    ON contrats(contrats_date_fin);
CREATE INDEX idx_contrats_type        ON contrats(contrats_type_id);
CREATE INDEX idx_contrats_service     ON contrats(contrats_service);
CREATE INDEX idx_contrats_responsable ON contrats(contrats_responsable_id);
CREATE INDEX idx_contrats_dashboard   ON contrats(contrats_type_id, contrats_statut, contrats_date_fin);

CREATE OR REPLACE FUNCTION update_modif_date() RETURNS TRIGGER AS $$
BEGIN
    NEW.contrats_date_modification = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contrats_modif
    BEFORE UPDATE ON contrats
    FOR EACH ROW EXECUTE FUNCTION update_modif_date();

-- ---------------------------------------------------------------------
-- PARTIES PRENANTES
-- ---------------------------------------------------------------------
CREATE TABLE parties_contrat (
    parties_contrat_id          BIGSERIAL PRIMARY KEY,
    parties_contrat_contrat_id  INTEGER       NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    parties_contrat_nom         VARCHAR(150)  NOT NULL,
    parties_contrat_type        VARCHAR(50)   CHECK (parties_contrat_type IN ('FOURNISSEUR', 'CLIENT', 'PARTENAIRE', 'PRESTATAIRE', 'AUTRE')),
    parties_contrat_email       VARCHAR(150),
    parties_contrat_telephone   VARCHAR(30)
);
CREATE INDEX idx_parties_contrat_id ON parties_contrat(parties_contrat_contrat_id);

-- ---------------------------------------------------------------------
-- DOCUMENTS
-- ---------------------------------------------------------------------
CREATE TABLE documents_contrat (
    documents_contrat_id           BIGSERIAL PRIMARY KEY,
    documents_contrat_contrat_id   INTEGER       NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    documents_contrat_nom_fichier  VARCHAR(255)  NOT NULL,
    documents_contrat_chemin       VARCHAR(500)  NOT NULL,
    documents_contrat_type         VARCHAR(50),
    documents_contrat_categorie    VARCHAR(30)   NOT NULL DEFAULT 'JUSTIFICATIF'
                      CHECK (documents_contrat_categorie IN ('JUSTIFICATIF', 'CONTRAT_SIGNE')),
    documents_contrat_version      INTEGER       NOT NULL DEFAULT 1,
    documents_contrat_importe_par  INTEGER       REFERENCES utilisateurs(utilisateurs_id),
    documents_contrat_date_import  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_contrat_contrat ON documents_contrat(documents_contrat_contrat_id);

-- ---------------------------------------------------------------------
-- REGLES DE VALIDATION
-- ---------------------------------------------------------------------
CREATE TABLE regles_validation (
    regles_validation_id               BIGSERIAL PRIMARY KEY,
    regles_validation_type_contrat_id  BIGINT        REFERENCES types_contrat(types_contrat_id) ON DELETE CASCADE,
    regles_validation_montant_minimum  NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (regles_validation_montant_minimum >= 0),
    regles_validation_montant_maximum  NUMERIC(15,2),
    regles_validation_niveau           INTEGER       NOT NULL CHECK (regles_validation_niveau > 0),
    regles_validation_role_requis      VARCHAR(30)   NOT NULL
                      CHECK (regles_validation_role_requis = 'VALIDEUR'),
    regles_validation_valideur_id      INTEGER       REFERENCES utilisateurs(utilisateurs_id) ON DELETE SET NULL,
    CONSTRAINT chk_montants CHECK (regles_validation_montant_maximum IS NULL OR regles_validation_montant_maximum > regles_validation_montant_minimum)
);

-- ---------------------------------------------------------------------
-- VALIDATIONS
-- ---------------------------------------------------------------------
CREATE TABLE validations (
    validations_id               BIGSERIAL PRIMARY KEY,
    validations_contrat_id       INTEGER     NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    validations_valideur_id      INTEGER     REFERENCES utilisateurs(utilisateurs_id),
    validations_demandeur_id     INTEGER     REFERENCES utilisateurs(utilisateurs_id),
    validations_niveau           INTEGER     NOT NULL CHECK (validations_niveau > 0),
    validations_role_requis      VARCHAR(30) NOT NULL
                     CHECK (validations_role_requis = 'VALIDEUR'),
    validations_cycle            INTEGER     NOT NULL DEFAULT 1 CHECK (validations_cycle > 0),
    validations_decision         VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE'
                     CHECK (validations_decision IN ('EN_ATTENTE', 'A_VENIR', 'NON_REQUISE', 'APPROUVE', 'REJETE', 'MODIFICATION_DEMANDEE')),
    validations_commentaire      TEXT,
    validations_date_soumission  TIMESTAMP   NOT NULL DEFAULT NOW(),
    validations_date_validation  TIMESTAMP
);

CREATE INDEX idx_validations_contract ON validations(validations_contrat_id);
CREATE INDEX idx_validations_decision ON validations(validations_decision);
CREATE UNIQUE INDEX uq_validation_cycle_niveau ON validations(validations_contrat_id, validations_cycle, validations_niveau);

-- Équivalent des contraintes de la migration multi-niveaux : les étapes
-- futures et celles annulées par un rejet/demande de correction sont conservées.

-- ---------------------------------------------------------------------
-- AVENANTS
-- ---------------------------------------------------------------------
CREATE TABLE avenants (
    avenants_id              BIGSERIAL PRIMARY KEY,
    avenants_contrat_id      INTEGER       NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    avenants_numero          INTEGER       NOT NULL CHECK (avenants_numero > 0),
    avenants_description     TEXT          NOT NULL,
    avenants_modifications   TEXT,
    avenants_date_avenant    DATE          NOT NULL,
    avenants_montant_avant   NUMERIC(15,2) CHECK (avenants_montant_avant > 0),
    avenants_montant_apres   NUMERIC(15,2) CHECK (avenants_montant_apres > 0),
    avenants_date_fin_avant  DATE,
    avenants_date_fin_apres  DATE,
    avenants_document_id     BIGINT        REFERENCES documents_contrat(documents_contrat_id) ON DELETE SET NULL,
    avenants_cree_par        INTEGER       REFERENCES utilisateurs(utilisateurs_id),
    avenants_date_creation   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_avenant_numero UNIQUE (avenants_contrat_id, avenants_numero)
);

-- ---------------------------------------------------------------------
-- VERSIONS
-- ---------------------------------------------------------------------
CREATE TABLE versions_contrat (
    versions_contrat_id             BIGSERIAL PRIMARY KEY,
    versions_contrat_contrat_id     INTEGER   NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    versions_contrat_version        INTEGER   NOT NULL CHECK (versions_contrat_version > 0),
    versions_contrat_contenu        JSONB     NOT NULL,
    versions_contrat_date_creation  TIMESTAMP NOT NULL DEFAULT NOW(),
    versions_contrat_cree_par       INTEGER   REFERENCES utilisateurs(utilisateurs_id),
    versions_contrat_avenant_id     BIGINT    REFERENCES avenants(avenants_id) ON DELETE SET NULL,
    CONSTRAINT uq_version UNIQUE (versions_contrat_contrat_id, versions_contrat_version)
);

-- ---------------------------------------------------------------------
-- PARAMETRAGE ALERTES
-- ---------------------------------------------------------------------
CREATE TABLE parametres_alertes (
    parametres_alertes_id          BIGSERIAL PRIMARY KEY,
    parametres_alertes_jours_avant INTEGER NOT NULL UNIQUE CHECK (parametres_alertes_jours_avant > 0),
    parametres_alertes_actif       BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    notifications_id                  BIGSERIAL PRIMARY KEY,
    notifications_utilisateur_id      INTEGER       NOT NULL REFERENCES utilisateurs(utilisateurs_id) ON DELETE CASCADE,
    notifications_contrat_id          INTEGER       REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    notifications_parametre_alerte_id BIGINT        REFERENCES parametres_alertes(parametres_alertes_id) ON DELETE SET NULL,
    notifications_titre               VARCHAR(150)  NOT NULL,
    notifications_message             VARCHAR(500)  NOT NULL,
    notifications_type                VARCHAR(30)   NOT NULL
                   CHECK (notifications_type IN ('ECHEANCE', 'RENOUVELLEMENT', 'RESILIATION', 'VALIDATION', 'AVENANT', 'INFO')),
    notifications_lu                  BOOLEAN       NOT NULL DEFAULT FALSE,
    notifications_date_creation       TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications(notifications_utilisateur_id, notifications_lu);
CREATE UNIQUE INDEX uq_notifications_echeance_destinataire
    ON notifications(notifications_utilisateur_id, notifications_contrat_id, notifications_parametre_alerte_id)
    WHERE notifications_parametre_alerte_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- AUDIT
-- ---------------------------------------------------------------------
CREATE TABLE journaux_audit (
    journaux_audit_id                 BIGSERIAL PRIMARY KEY,
    journaux_audit_utilisateur_id     INTEGER     REFERENCES utilisateurs(utilisateurs_id) ON DELETE SET NULL,
    journaux_audit_contrat_id         INTEGER     REFERENCES contrats(contrats_id) ON DELETE SET NULL,
    journaux_audit_reference_contrat  VARCHAR(50),
    journaux_audit_action             VARCHAR(50) NOT NULL,
    journaux_audit_ancienne_valeur    TEXT,
    journaux_audit_nouvelle_valeur    TEXT,
    journaux_audit_date_action        TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_contract ON journaux_audit(journaux_audit_contrat_id);
CREATE INDEX idx_audit_date     ON journaux_audit(journaux_audit_date_action);

-- =====================================================================
-- DONNEES DE REFERENCE
-- =====================================================================
INSERT INTO parametres_alertes (parametres_alertes_jours_avant) VALUES (90), (60), (30), (15), (7);

INSERT INTO types_contrat (types_contrat_nom, types_contrat_description) VALUES
    ('Fournisseur', 'Contrats d''achat auprès de fournisseurs'),
    ('Client',      'Contrats de vente / prestations clients'),
    ('Partenaire',  'Accords de partenariat'),
    ('Prestataire', 'Contrats de prestation de services'),
    ('Maintenance', 'Contrats de maintenance et support'),
    ('Location',    'Contrats de location (locaux, matériel, véhicules)'),
    ('Service',     'Abonnements et contrats de services divers');

-- ---------------------------------------------------------------------
-- UTILISATEURS PAR DEFAUT
-- Comptes repris du dump suiviContrats.sql; les mots de passe sont déjà hachés.
-- ---------------------------------------------------------------------
INSERT INTO utilisateurs
    (utilisateurs_id, utilisateurs_matricule, utilisateurs_nom, utilisateurs_prenom,
     utilisateurs_email, utilisateurs_mot_de_passe, utilisateurs_role,
     utilisateurs_service, utilisateurs_actif, utilisateurs_secret_2fa,
     utilisateurs_enabled_2fa, utilisateurs_date_creation,
     utilisateurs_changement_mot_de_passe_requis)
VALUES
    (1, 2026044, 'admin', 'test', 'benaybody@gmail.com',
     '$2a$10$0gKugI2cAR.IoQW9Q3hAnu7LaH1jz14agmLHrXRqysQh2uns9j39O',
     'ADMIN', 'Administration', TRUE, NULL, FALSE, '2026-10-16 15:13:47.437274', FALSE),
    (11, 2002044, 'Simon', 'Riley', 'simonrileyoneforone@gmail.com',
     '$2a$10$BfqkfUTbwxtASWQssl9Xt.rqVcIrd2LcIryfuW/6nfaoK/9AI4H8m',
     'GESTIONNAIRE', 'gestion', TRUE, NULL, FALSE, '2026-10-20 13:56:49.890122', FALSE),
    (13, 2003044, 'John', 'Price', 'dinohrakotomamonjy8@gmail.com',
     '$2a$10$a.eHGjHYNji.1Wp55YljGeVMmQrObbpCjbTcGyAQG1KHw/0B0ivfW',
     'VALIDEUR', 'valideur', TRUE, NULL, FALSE, '2026-10-22 09:19:44.417556', FALSE),
    (15, 2006044, 'Myah', 'Nou', 'erickamanou@gmail.com',
     '$2a$10$NwN5kZvixJV3qjV9g2DPWeA12pL0kO2x5R.wogH6bEnnH4axFv1DS',
     'JURIDIQUE', 'Juridique', TRUE, NULL, FALSE, '2026-10-22 13:19:25.649753', FALSE);

-- Les identifiants étant importés, la séquence repart après le dernier compte.
SELECT setval(pg_get_serial_sequence('utilisateurs', 'utilisateurs_id'),
              (SELECT MAX(utilisateurs_id) FROM utilisateurs), TRUE);
