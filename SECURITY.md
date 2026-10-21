# Configuration des secrets

Pour faciliter le développement local, `application.properties` fournit un secret JWT et un mot de passe PostgreSQL de développement par défaut. Le secret JWT est marqué `CLM-DEV-ONLY` et ne doit jamais être utilisé au déploiement. Les variables d'environnement ci-dessous remplacent ces valeurs par défaut.

Définissez ces variables dans l'environnement qui lance Spring Boot :

- `CLM_JWT_SECRET` : secret aléatoire long à définir avant le déploiement.
- `DB_PASSWORD` : mot de passe de l'utilisateur PostgreSQL pour l'environnement cible.
- `DB_USERNAME` (facultatif, valeur par défaut `postgres`).
- `DB_URL` (facultatif, valeur locale par défaut dans `application.properties`).

Exemple de génération d'une clé sous Bash :

```bash
openssl rand -base64 48
```

Pour le déploiement, copiez le résultat dans le gestionnaire de secrets ou l'environnement du serveur. Ne remplacez pas le secret de développement par une autre valeur committée dans le dépôt.

Les jetons expirent après 30 minutes. Un compte désactivé ou dont le rôle a changé est rechargé depuis la base à chaque requête et perd immédiatement l'accès correspondant. Si l'authentification à deux facteurs est activée pour un compte, son code à six chiffres est requis à la connexion.
