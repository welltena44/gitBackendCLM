-- Autorise les rôles personnalisés saisis pour les parties d'un contrat.
-- Compatible avec les schémas créés avant que le champ type ne soit libre.
ALTER TABLE parties_contrat
    ALTER COLUMN parties_contrat_type TYPE VARCHAR(100);

ALTER TABLE parties_contrat
    DROP CONSTRAINT IF EXISTS parties_contrat_parties_contrat_type_check;
