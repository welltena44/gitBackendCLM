-- Migration non destructive : à appliquer une fois sur une base existante.
-- Les statuts d'étape futurs sont créés lors de la soumission d'un circuit multi-niveaux.
ALTER TABLE IF EXISTS public.validations
    DROP CONSTRAINT IF EXISTS validations_validations_decision_check;
ALTER TABLE IF EXISTS public.validations
    DROP CONSTRAINT IF EXISTS chk_validations_decision;
ALTER TABLE IF EXISTS public.validations
    ADD CONSTRAINT chk_validations_decision
    CHECK (validations_decision IN (
        'EN_ATTENTE', 'A_VENIR', 'NON_REQUISE', 'APPROUVE', 'REJETE', 'MODIFICATION_DEMANDEE'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_echeance_destinataire
    ON public.notifications (notifications_utilisateur_id, notifications_contrat_id, notifications_parametre_alerte_id)
    WHERE notifications_parametre_alerte_id IS NOT NULL;
