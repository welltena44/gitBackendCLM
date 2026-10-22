-- Migration non destructive pour une base CLM déjà créée.
-- À exécuter une seule fois (le script est aussi idempotent) sur la base
-- référencée par spring.datasource.url dans application.properties.

BEGIN;

-- Les règles du circuit peuvent être attribuées à un valideur précis.
ALTER TABLE regles_validation
    ADD COLUMN IF NOT EXISTS regles_validation_valideur_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_regles_validation_valideur'
          AND conrelid = 'regles_validation'::regclass
    ) THEN
        ALTER TABLE regles_validation
            ADD CONSTRAINT fk_regles_validation_valideur
            FOREIGN KEY (regles_validation_valideur_id)
            REFERENCES utilisateurs(utilisateurs_id) ON DELETE SET NULL;
    END IF;
END $$;

-- Les décisions appartiennent au rôle VALIDEUR, quel que soit le rôle
-- historique qui était enregistré sur les étapes.
UPDATE regles_validation
SET regles_validation_role_requis = 'VALIDEUR'
WHERE regles_validation_role_requis <> 'VALIDEUR';

UPDATE validations
SET validations_role_requis = 'VALIDEUR'
WHERE validations_role_requis <> 'VALIDEUR';

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'regles_validation'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%role_requis%'
    LOOP
        EXECUTE format('ALTER TABLE regles_validation DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE regles_validation
        ADD CONSTRAINT chk_regles_validation_role_valideur
        CHECK (regles_validation_role_requis = 'VALIDEUR');

    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'validations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%role_requis%'
    LOOP
        EXECUTE format('ALTER TABLE validations DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE validations
        ADD CONSTRAINT chk_validations_role_valideur
        CHECK (validations_role_requis = 'VALIDEUR');
END $$;

-- Distingue les justificatifs du PDF réellement signé à l'extérieur.
ALTER TABLE documents_contrat
    ADD COLUMN IF NOT EXISTS documents_contrat_categorie VARCHAR(30) NOT NULL DEFAULT 'JUSTIFICATIF';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_documents_contrat_categorie'
          AND conrelid = 'documents_contrat'::regclass
    ) THEN
        ALTER TABLE documents_contrat
            ADD CONSTRAINT chk_documents_contrat_categorie
            CHECK (documents_contrat_categorie IN ('JUSTIFICATIF', 'CONTRAT_SIGNE'));
    END IF;
END $$;

COMMIT;
