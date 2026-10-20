-- À exécuter une seule fois sur la base PostgreSQL existante.
CREATE TABLE IF NOT EXISTS demandes_reinitialisation_mot_de_passe (
    demande_id BIGSERIAL PRIMARY KEY,
    utilisateur_id INTEGER NOT NULL
        REFERENCES utilisateurs(utilisateurs_id) ON DELETE CASCADE,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
        CHECK (statut IN ('EN_ATTENTE', 'APPROUVEE', 'REJETEE')),
    date_creation TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_demandes_reinitialisation_statut_date
    ON demandes_reinitialisation_mot_de_passe (statut, date_creation);
