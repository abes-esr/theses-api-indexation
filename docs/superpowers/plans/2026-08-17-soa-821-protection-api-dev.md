# SOA-821 — Protection et déploiement DEV de l’API d’indexation

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Déployer en DEV l’API d’administration `theses-api-indexation` en service permanent, accessible uniquement via un chemin Shibboleth protégé et une liste fermée d’ePPN, avec des droits Elasticsearch minimaux.

**Architecture:** Spring Security transforme l’en-tête Shibboleth `eppn` en identité applicative autorisée et le contrôleur l’utilise comme `updatedBy`. Le service écoute uniquement sur le réseau Docker au port 8994 ; `theses-rp` publie le chemin administratif protégé. Le compte d’écriture conserve son rôle limité à `referencement` et le compte de recherche reçoit un rôle de lecture dédié.

**Tech Stack:** Java 17, Spring Boot 3.0.6, Spring Security, Spring Boot Actuator, JUnit 5, MockMvc, Maven, Docker, Docker Compose, Elasticsearch 8, Apache/Shibboleth.

---

### Task 1: Verrouiller l’identité ePPN et l’autorisation HTTP

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/fr/abes/thesesapiindexation/security/NoIndexSecurityProperties.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/security/ShibbolethEppnAuthenticationFilter.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/security/ReferencementSecurityConfiguration.java`
- Create: `src/test/java/fr/abes/thesesapiindexation/security/NoIndexSecurityPropertiesTest.java`
- Create: `src/test/java/fr/abes/thesesapiindexation/security/ReferencementSecurityTest.java`

**Step 1: Ajouter les tests rouges de normalisation de la liste**

Tester que `" Agent@Abes.fr,agent@abes.fr , second@abes.fr "` produit exactement `agent@abes.fr` et `second@abes.fr`, qu’une valeur vide produit une liste vide et qu’aucun joker n’est accepté.

**Step 2: Ajouter les tests rouges de sécurité HTTP**

Avec `@WebMvcTest`, couvrir :

- absence de l’en-tête `eppn` sur le PUT : `401` ;
- ePPN présent mais absent de la liste : `403` ;
- ePPN autorisé, y compris avec une casse différente : la requête atteint le contrôleur ;
- valeurs multiples de `eppn` : refus ;
- route inconnue : refus ;
- `GET /actuator/health` sans ePPN : autorisé.

Exécuter :

```powershell
mvn -Dtest=NoIndexSecurityPropertiesTest,ReferencementSecurityTest test
```

Résultat attendu : échec de compilation car les classes de sécurité n’existent pas encore.

**Step 3: Ajouter les dépendances minimales**

Ajouter dans `pom.xml` :

- `spring-boot-starter-security` ;
- `spring-boot-starter-actuator` ;
- `spring-security-test` avec le scope `test`.

**Step 4: Implémenter la propriété typée**

Créer une propriété `referencement.security.allowed-eppns` alimentée par `${THESES_NOINDEX_ALLOWED_EPPNS:}`. À la construction, découper sur les virgules, supprimer les espaces, convertir en minuscules avec `Locale.ROOT`, retirer les doublons et rejeter toute entrée contenant `*`. Une configuration vide doit refuser tout ePPN.

**Step 5: Implémenter le filtre Shibboleth**

Le filtre doit :

- lire uniquement l’en-tête `eppn` ;
- ne créer aucune authentification si l’en-tête manque ;
- refuser les valeurs vides ou multiples ;
- normaliser l’ePPN ;
- créer une authentification `NOINDEX_ADMIN` seulement si l’ePPN est autorisé ;
- mémoriser une identité non autorisée sans lui donner de rôle, afin d’obtenir `403` et non `401`.

Ne jamais journaliser la liste d’ePPN ni les en-têtes d’authentification.

**Step 6: Déclarer la chaîne de sécurité**

Sous le profil `!init-index & !import-robots`, configurer une API sans session, CSRF désactivé, sans CORS permissif :

- `/actuator/health` accessible sans authentification ;
- `PUT /api/v1/referencements/**` réservé à `NOINDEX_ADMIN` ;
- tout le reste refusé ;
- réponses `401` et `403` en `application/problem+json` sans information sensible.

**Step 7: Exécuter les tests ciblés puis toute la suite**

```powershell
mvn -Dtest=NoIndexSecurityPropertiesTest,ReferencementSecurityTest test
mvn test
```

Résultat attendu : tous les tests sont verts.

**Step 8: Commit**

```powershell
git add pom.xml src/main/resources/application.properties src/main/java/fr/abes/thesesapiindexation/security src/test/java/fr/abes/thesesapiindexation/security
git commit -m "feat: protéger l’API par une liste d’ePPN"
```

---

### Task 2: Rendre `updatedBy` infalsifiable

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteRequest.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementController.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementControllerTest.java`
- Modify: `README.md`

**Step 1: Adapter les tests du contrôleur avant le code**

Configurer le test avec un ePPN autorisé et couvrir :

- corps sans `updatedBy` + en-tête `eppn: Agent@Abes.fr` : `200` et commande contenant `updatedBy=agent@abes.fr` ;
- champ JSON `updatedBy` envoyé par le client : `400` ;
- les validations existantes `pageType`, `noIndex`, `demandeRef`, identifiant et indisponibilité Elasticsearch restent actives.

Exécuter :

```powershell
mvn -Dtest=ReferencementControllerTest test
```

Résultat attendu : échec, car le contrat accepte encore `updatedBy` dans le corps.

**Step 2: Retirer `updatedBy` de la requête HTTP**

Supprimer ce composant du record `ReferencementWriteRequest` et remplacer `toCommand()` par `toCommand(String updatedBy)`. Ne pas modifier `ReferencementWriteCommand`, `ReferencementDocument` ni l’importeur du `robots.txt`.

**Step 3: Injecter l’identité Spring Security dans le contrôleur**

Ajouter `Authentication` au handler PUT et transmettre `authentication.getName()` à `request.toCommand(...)`. La normalisation ayant déjà été faite par le filtre, la valeur stockée doit être l’ePPN canonique en minuscules.

**Step 4: Mettre à jour le contrat documenté**

Dans `README.md`, retirer `updatedBy` du JSON d’entrée, documenter l’en-tête fourni exclusivement par Shibboleth, la liste `THESES_NOINDEX_ALLOWED_EPPNS`, les codes `401`/`403` et préciser que l’auteur retourné est calculé par le serveur.

**Step 5: Vérifier et committer**

```powershell
mvn -Dtest=ReferencementControllerTest test
mvn test
git add src/main/java/fr/abes/thesesapiindexation/referencement src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementControllerTest.java README.md
git commit -m "feat: attribuer les écritures à l’ePPN authentifié"
```

---

### Task 3: Ajouter la santé du service et le healthcheck d’image

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `Dockerfile`
- Create: `src/test/java/fr/abes/thesesapiindexation/HealthEndpointTest.java`
- Modify: `README.md`

**Step 1: Écrire le test rouge de surface Actuator**

Vérifier que `/actuator/health` répond `200` sans ePPN et que `/actuator/env` n’est pas exposé.

**Step 2: Restreindre Actuator**

Configurer :

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

**Step 3: Installer l’outil du healthcheck dans l’image**

Dans l’étage runtime du `Dockerfile`, installer `curl` avec le gestionnaire de paquets de l’image puis nettoyer le cache. Ne pas ajouter de secret ou de configuration d’environnement à l’image.

**Step 4: Vérifier l’application et l’image**

```powershell
mvn -Dtest=HealthEndpointTest test
mvn test
docker build --tag theses-api-indexation:soa-821-local .
docker run --rm --entrypoint curl theses-api-indexation:soa-821-local --version
```

Résultat attendu : tests verts, image construite, `curl` disponible.

**Step 5: Commit**

```powershell
git add src/main/resources/application.properties Dockerfile src/test/java/fr/abes/thesesapiindexation/HealthEndpointTest.java README.md
git commit -m "feat: exposer la santé interne de l’API"
```

---

### Task 4: Déclarer le service permanent et les droits Elasticsearch

**Repository:** `C:/Users/villiseck.LEVANT/Documents/Plateformes/theses/theses-docker`

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env-dist`
- Modify: `README.md`

**Step 1: Créer une branche isolée depuis `origin/develop`**

Créer un worktree dédié sur la branche `SOA-821-api-indexation-dev`. Ne pas réutiliser la branche ni le worktree SOA-820.

**Step 2: Capturer les assertions Compose avant modification**

Les contrôles doivent initialement montrer que le service permanent et le chemin protégé n’existent pas encore. Les assertions finales vérifieront structurellement le résultat de `docker compose config`, sans dépendre du démarrage de DEV.

**Step 3: Ajouter le rôle de lecture Elasticsearch**

Dans `theses-elasticsearch-setupusers` :

- créer `theses-noindex-reader` avec `read` et `view_index_metadata` sur `${THESES_REFERENCEMENT_INDEX}` ;
- ajouter ce rôle au compte `${THESES_API_RECHERCHE_ELASTIC_USERNAME}` en complément de `viewer` ;
- conserver `theses-noindex-writer` et son compte sans élargir leurs privilèges ;
- laisser `elastic` réservé à l’initialisation.

**Step 4: Ajouter le service permanent**

Déclarer `theses-api-indexation` hors du profil `referencement-jobs` avec :

- l’image `abesesr/theses:${THESES_API_INDEXATION_VERSION}` ;
- `SERVER_PORT=8994` ;
- les variables Elasticsearch et `THESES_NOINDEX_ALLOWED_EPPNS` ;
- le certificat CA monté en lecture seule ;
- `expose: 8994`, sans `ports` ;
- `restart: unless-stopped` ;
- les limites CPU/mémoire et labels OpenTelemetry alignés sur les autres API ;
- un healthcheck `curl --fail http://127.0.0.1:8994/actuator/health` ;
- les dépendances saines vers Elasticsearch et le job de création des utilisateurs.

**Step 5: Ajouter la route Shibboleth protégée**

Dans `theses-rp`, ajouter :

```yaml
RENATER_SP_HTTPD_PROTECTED_PATH_2: "/api/v1/indexation/"
RENATER_SP_HTTPD_PROTECTED_PROXY_TO_2: "http://theses-api-indexation:8994/api/v1/referencements/"
```

Ne jamais déclarer ce chemin dans une variable `PUBLIC_PATH`.

**Step 6: Compléter `.env-dist` et la documentation**

Ajouter `THESES_NOINDEX_ALLOWED_EPPNS=` avec la règle « liste vide = aucun accès ». Ne mettre aucun ePPN réel ni mot de passe dans Git. Documenter le service, son absence de port hôte et la route externe.

**Step 7: Valider Compose**

Avec un fichier d’environnement local dérivé de `.env-dist`, exécuter :

```powershell
docker compose config --quiet
docker compose --profile referencement-jobs config --quiet
docker compose config --format json
```

Contrôler dans le JSON : service présent sans profil, aucun port publié, port
8994 seulement exposé, healthcheck présent, certificat en lecture seule, route
exclusivement protégée, rôles reader/writer exacts. Vérifier aussi que l’API
n’est pas membre du réseau Docker partagé : un réseau interne ne contient que
le proxy et l’API, et un second ne contient qu’Elasticsearch et l’API. Exécuter
aussi `git diff --check`.

**Step 8: Commit**

```powershell
git add docker-compose.yml .env-dist README.md
git commit -m "feat: déployer l’API d’indexation protégée"
```

---

### Task 5: Publier et vérifier les deux branches

**Files:**
- Inspect: working trees and remote pull requests in both repositories.

**Step 1: Vérification finale de l’API**

```powershell
mvn clean test
docker build --tag theses-api-indexation:soa-821-local .
git diff --check
git status --short
```

Exiger une suite Maven verte et un arbre propre.

**Step 2: Vérification finale de Docker**

```powershell
docker compose config --quiet
docker compose --profile referencement-jobs config --quiet
git diff --check
git status --short
```

**Step 3: Publier les branches et ouvrir les PR**

Pousser les branches SOA-821 des deux dépôts et ouvrir deux PR vers `develop`, prêtes pour revue. Les descriptions doivent référencer SOA-821, résumer la sécurité, les tests et préciser qu’aucun environnement TEST/PROD n’est touché.

**Step 4: Attendre les contrôles CI**

Ne fusionner qu’après contrôles verts et revue. Après fusion de l’API, vérifier la publication du tag `develop-api-indexation` correspondant au SHA fusionné avant le déploiement DEV.

---

### Task 6: Déployer et recetter en DEV sans recréer l’index

**Files:**
- Modify on DEV only: `.env` du déploiement `theses-docker` (non versionné)

**Step 1: Préparer la configuration DEV**

Sur Diplotaxis DEV, mettre à jour le dépôt `theses-docker` sur `develop`. Renseigner dans `.env` la version d’image publiée et `THESES_NOINDEX_ALLOWED_EPPNS` avec l’ePPN effectivement renvoyé par la session Shibboleth de l’agent recette. Ne jamais afficher ou enregistrer les mots de passe dans le rapport.

**Step 2: Mettre à jour les rôles Elasticsearch**

Recréer uniquement `theses-elasticsearch-setupusers` et exiger un code `0`. Vérifier via `_security/user/_privileges` que :

- le writer lit/écrit uniquement `referencement` et ne peut ni créer ni supprimer un index ;
- le reader peut lire `referencement` et ne peut pas écrire.

**Step 3: Vérifier le mapping sans mutation**

Exécuter le job `init-index`, qui doit constater l’index compatible et sortir avec le code `0`. Ne supprimer, fermer, recréer ou réindexer aucun index.

**Step 4: Démarrer l’API puis le proxy**

Démarrer/recréer `theses-api-indexation`, attendre l’état `healthy`, puis recréer `theses-rp`. En cas d’échec, arrêter la procédure et recueillir les journaux ciblés sans dévoiler les secrets.

**Step 5: Recette de sécurité**

Vérifier successivement :

- URL externe sans session : redirection Shibboleth, jamais accès direct ;
- session avec ePPN non autorisé : `403` ;
- ePPN autorisé : PUT accepté ;
- le port 8994 n’est pas publié sur l’hôte ;
- `/actuator/health` n’est pas routé publiquement.

**Step 6: Recette fonctionnelle réversible**

Sur un identifiant DEV convenu, envoyer un PUT avec `noIndex: true`, répéter la même requête pour vérifier l’idempotence, puis envoyer `noIndex: false`. Vérifier dans Elasticsearch que `updatedBy` est exactement l’ePPN normalisé et que la seconde requête identique ne change pas `updatedAt`.

**Step 7: Non-régression SOA-820**

Relancer `init-index` et `import-robots` avec leur profil ponctuel. Exiger les codes `0` et vérifier que le nombre de documents existants n’a pas diminué.

**Step 8: Rapport de recette**

Consigner dans SOA-821 : SHA des deux merges, digest de l’image, état des conteneurs, résultats anonymisés des tests `401/403/200`, identifiant DEV testé, contrôles de privilèges, résultats des jobs et confirmation qu’aucun environnement TEST/PROD n’a été modifié.

---

## Garde-fous d’exécution

- Arrêter chaque séquence au premier code non nul.
- Ne jamais purger, recréer ou réindexer `referencement`.
- Ne jamais committer `.env`, ePPN réels, mots de passe ou certificats.
- Ne jamais intervenir en TEST ou PROD dans ce plan.
- Obtenir une preuve fraîche avant d’annoncer une étape terminée.
