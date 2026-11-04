# Automatisation du circuit d'approbation des avenants — SPAT-CLM

## Ce qui change

- La création d'un avenant ne modifie plus immédiatement le contrat actif.
- Le backend recherche le circuit actif avec `ReglesValidationService.getCircuit(type, montant_apres)`.
- Les validations de l'avenant sont enregistrées dans `validations` avec `validations_avenant_id`.
- Les étapes du premier niveau passent à `EN_ATTENTE`; les niveaux suivants restent `A_VENIR`.
- Une approbation active le niveau suivant. Pour `VALIDEUR_ET_JURIDIQUE`, les deux rôles du même niveau doivent approuver.
- Un rejet clôt le circuit de l'avenant sans modifier le contrat.
- Une demande de modification retourne l'avenant au créateur. Il peut le corriger avec `PUT /api/avenants/{id}`, puis le resoumettre avec `POST /api/avenants/{id}/resoumettre`.
- Après la dernière approbation, le contrat est mis à jour et une nouvelle version est créée.
- Les notifications sont enregistrées dans l'application et un e-mail est tenté. Un échec SMTP ne supprime pas la notification interne.

## Fichiers Java modifiés

- `src/main/java/com/suivicontrats/front/entity/Avenants.java`
- `src/main/java/com/suivicontrats/front/entity/Validations.java`
- `src/main/java/com/suivicontrats/front/repository/ValidationsRepository.java`
- `src/main/java/com/suivicontrats/front/service/AvenantsService.java`
- `src/main/java/com/suivicontrats/front/service/ValidationsService.java`
- `src/main/java/com/suivicontrats/front/controller/AvenantsController.java`

## Migration PostgreSQL obligatoire

1. Faire une sauvegarde de la base.
2. Depuis Ubuntu, exécuter la migration une seule fois :

```bash
psql -U postgres -d suiviContrats -f CLM_MIGRATION_AVENANTS_APPROBATION.sql
```

Cette commande correspond aux valeurs par défaut de `application.properties`. Si tu utilises d'autres valeurs pour `DB_USERNAME` ou `DB_URL`, adapte l'utilisateur et le nom de la base. Ne lance pas la migration sur une base différente de celle utilisée par l'application.

## Vérifier les e-mails

Le projet utilise déjà `EmailService`. Configurer `MAIL_USERNAME` et `MAIL_PASSWORD` dans l'environnement du backend. Les notifications internes restent disponibles même si le SMTP est indisponible.

## Endpoints

- `POST /api/avenants` : crée l'avenant et soumet son circuit automatiquement.
- `PUT /api/avenants/{id}` : corrige un avenant uniquement lorsque son statut est `MODIFICATION_DEMANDEE`.
- `POST /api/avenants/{id}/resoumettre` : ouvre le cycle suivant après correction.
- `GET /api/avenants/{id}` et `GET /api/avenants/contrat/{contratId}` : consultation selon les droits existants.
- `POST /api/validations/{validationId}/decider` : décision VALIDEUR/JURIDIQUE, comme pour les validations existantes.

## Scénario de test conseillé

1. Créer un contrat actif dont le type et le montant sont couverts par des règles actives.
2. Créer un avenant qui change le montant ou la date de fin.
3. Vérifier que le contrat n'a pas encore changé et que l'avenant est `EN_VALIDATION`.
4. Vérifier les validations `EN_ATTENTE` du premier niveau et les notifications reçues.
5. Approuver les étapes dans l'ordre. Pour une étape conjointe, approuver avec VALIDEUR puis JURIDIQUE.
6. Vérifier qu'avant la dernière approbation le contrat reste inchangé.
7. Après la dernière approbation, vérifier le nouveau montant/la nouvelle date, le statut `APPROUVE` de l'avenant et la nouvelle version.
8. Refaire le test avec un rejet et une demande de modification : le contrat doit rester inchangé.

## Limites à connaître

- Les règles de validation actives sont réutilisées telles qu'elles sont définies aujourd'hui. Le montant utilisé pour choisir le circuit est le montant après avenant (ou le montant courant si seul la date change).
- Les avenants actuellement enregistrés avant cette migration sont marqués `APPROUVE`, car l'ancienne logique avait déjà appliqué leurs modifications.
- L'envoi SMTP est synchrone dans la transaction métier mais les exceptions d'e-mail sont interceptées par `NotificationsService`; pour une architecture SaaS à fort volume, il faudra ensuite passer à une file d'événements/outbox asynchrone.
