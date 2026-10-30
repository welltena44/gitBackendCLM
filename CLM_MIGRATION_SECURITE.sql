-- Migration CLM sécurité (PostgreSQL). Faire une sauvegarde avant exécution.
-- Ajoute le verrouillage temporaire après plusieurs échecs de connexion.
ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS utilisateurs_tentatives_echec INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS utilisateurs_verrouille_jusqua TIMESTAMP;

-- Versionnement optimiste des contrats pour éviter les écrasements concurrents.
ALTER TABLE contrats
    ADD COLUMN IF NOT EXISTS contrats_version_optimiste BIGINT NOT NULL DEFAULT 0;

-- Autorise les deux types de notifications utilisés par le workflow des règles.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_notifications_type_check
    CHECK (notifications_type IN (
        'ECHEANCE', 'RENOUVELLEMENT', 'RESILIATION', 'VALIDATION', 'AVENANT', 'INFO',
        'REGLE_VALIDATION', 'SEUIL_A_CONFIRMER'
    ));
