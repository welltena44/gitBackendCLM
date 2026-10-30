# Correctifs de sécurité inclus

## Configuration obligatoire

Les valeurs par défaut dangereuses pour le mot de passe PostgreSQL et la clé JWT ont été retirées. Avant le démarrage, définir `DB_PASSWORD` et `CLM_JWT_SECRET` (au moins 32 caractères aléatoires). Voir `.env.example` pour la liste des variables. Ne jamais committer les valeurs réelles.

## Migration d'une base existante

Faire une sauvegarde PostgreSQL, puis exécuter `CLM_MIGRATION_SECURITE.sql` une seule fois sur la base CLM existante. Le script ajoute les compteurs de tentatives et la date de verrouillage, puis autorise les types de notifications utilisés par le workflow des règles.

Pour une nouvelle base vide, `DBContrat.sql` contient aussi les nouvelles colonnes et les types de notification. Ne pas exécuter le script de création sur une base déjà initialisée.

## Connexion

- Les échecs de mot de passe sont comptés par compte; cinq échecs déclenchent un verrouillage de 15 minutes.
- Si le compte a activé le 2FA, le code TOTP à six chiffres est exigé côté backend.
- Le changement de mot de passe forcé est vérifié dans le filtre API, et pas uniquement dans React.

Le secret TOTP est encore stocké dans la base sous forme lisible; pour un déploiement sensible, chiffrer les secrets 2FA avec une clé séparée stockée dans un gestionnaire de secrets.
