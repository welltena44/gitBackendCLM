-- Migration incrémentale : automatisations du cycle de vie des contrats.
-- À exécuter une seule fois sur une base déjà créée avec DBContrat.sql.

ALTER TABLE contrats
    ADD COLUMN IF NOT EXISTS contrats_date_archivage TIMESTAMP;

ALTER TABLE validations
    ADD COLUMN IF NOT EXISTS validations_date_rappel TIMESTAMP,
    ADD COLUMN IF NOT EXISTS validations_date_escalade TIMESTAMP;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS notifications_date_echeance DATE;

DROP INDEX IF EXISTS uq_notifications_echeance_destinataire;
CREATE UNIQUE INDEX uq_notifications_echeance_destinataire
    ON notifications(notifications_utilisateur_id, notifications_contrat_id,
                     notifications_parametre_alerte_id, notifications_date_echeance)
    WHERE notifications_parametre_alerte_id IS NOT NULL
      AND notifications_date_echeance IS NOT NULL;

CREATE TABLE IF NOT EXISTS obligations_contrat (
    obligations_contrat_id             BIGSERIAL PRIMARY KEY,
    obligations_contrat_contrat_id     INTEGER       NOT NULL REFERENCES contrats(contrats_id) ON DELETE CASCADE,
    obligations_contrat_titre          VARCHAR(150)  NOT NULL,
    obligations_contrat_description    TEXT,
    obligations_contrat_echeance       DATE,
    obligations_contrat_statut         VARCHAR(20)   NOT NULL DEFAULT 'EN_COURS'
                                    CHECK (obligations_contrat_statut IN ('EN_COURS', 'TERMINEE', 'ANNULEE')),
    obligations_contrat_responsable_id INTEGER       REFERENCES utilisateurs(utilisateurs_id) ON DELETE SET NULL,
    obligations_contrat_cree_par       INTEGER       REFERENCES utilisateurs(utilisateurs_id) ON DELETE SET NULL,
    obligations_contrat_date_creation  TIMESTAMP     NOT NULL DEFAULT NOW(),
    obligations_contrat_date_cloture   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_obligations_contrat_statut
    ON obligations_contrat(obligations_contrat_contrat_id, obligations_contrat_statut);
