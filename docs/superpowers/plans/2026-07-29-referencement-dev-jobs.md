# Referencement DEV Jobs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publier `theses-api-indexation` sous forme d’image Docker, ajouter
deux jobs ponctuels dans `theses-docker`, puis créer et alimenter l’index
`referencement` sur l’environnement DEV.

**Architecture:** Une image Java 17 unique exécute soit le profil Spring
`init-index`, soit le profil `import-robots`. Deux services Docker Compose,
activés uniquement par le profil manuel `referencement-jobs`, séparent les
droits administrateur de création des droits applicatifs limités à l’index.
L’import lit le `robots.txt` réel mais ne modifie ni ce fichier ni le
comportement public de theses.fr.

**Tech Stack:** Java 17, Maven, Spring Boot 3.0.6, Docker Buildx, Docker
Compose, Elasticsearch 8.10.3, GitHub Actions.

## Global Constraints

- Environnement cible unique : DEV.
- Ne démarrer aucun service HTTP `theses-api-indexation`.
- Ne publier aucun port.
- Ne modifier aucune directive du `robots.txt`.
- Utiliser `https://theses.fr/robots.txt` comme source de migration.
- Utiliser le compte administrateur uniquement pour `init-index`.
- Utiliser le compte `theses-api-indexation`, limité à `referencement`, pour
  `import-robots`.
- Ne jamais supprimer ou recréer automatiquement l’index.
- Arrêter la procédure au premier code de sortie non nul.
- Ne jamais afficher, commiter ou transmettre un secret dans une commande.
- Rédiger les commits en français avec l’auteur Git configuré
  `Jerome Villiseck`.
- Utiliser les branches `SOA-820-deploiement-dev` et
  `SOA-820-jobs-referencement-dev`.

---

### Task 1: Construire et publier l’image `theses-api-indexation`

**Repository:** `abes-esr/theses-api-indexation`

**Files:**
- Create: `.dockerignore`
- Create: `Dockerfile`
- Create: `.github/workflows/buildx-test-pubtodockerhub.yml`
- Verify: `pom.xml`
- Verify: `src/main/**`
- Verify: `src/test/**`

**Interfaces:**
- Consumes: le JAR Maven `theses-api-indexation-0.1.0-SNAPSHOT.jar`.
- Produces: la cible Docker `api-indexation-image` et l’image
  `abesesr/theses:develop-api-indexation`.

- [ ] **Step 1: Vérifier la branche et le socle Maven**

Run:

```powershell
git status --short --branch
mvn --batch-mode clean verify
```

Expected:

- branche `SOA-820-deploiement-dev` ;
- arbre propre ;
- `BUILD SUCCESS` ;
- 66 tests réussis, aucun échec.

- [ ] **Step 2: Ajouter les exclusions du contexte Docker**

Create `.dockerignore`:

```dockerignore
.git
.github
.idea
.worktrees
docs
target
*.iml
```

- [ ] **Step 3: Ajouter le Dockerfile multiétage**

Create `Dockerfile`:

```dockerfile
FROM maven:3-eclipse-temurin-17 AS build-image
WORKDIR /build

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode \
    -Dmaven.test.skip=false \
    -Duser.timezone=Europe/Paris \
    -Duser.language=fr \
    package

FROM eclipse-temurin:17-jre AS api-indexation-image
WORKDIR /app

COPY --from=build-image \
    /build/target/theses-api-indexation-0.1.0-SNAPSHOT.jar \
    /app/theses-api-indexation.jar

ENTRYPOINT ["java", "-jar", "/app/theses-api-indexation.jar"]
```

- [ ] **Step 4: Vérifier localement la construction de l’image**

Run:

```powershell
docker build `
  --target api-indexation-image `
  --tag theses-api-indexation:dev-test `
  .
docker image inspect theses-api-indexation:dev-test
```

Expected: la construction exécute la suite Maven et l’image possède un
`Entrypoint` Java vers `/app/theses-api-indexation.jar`.

- [ ] **Step 5: Ajouter le workflow de publication**

Create `.github/workflows/buildx-test-pubtodockerhub.yml`:

```yaml
name: "buildx-test-pubtodockerhub"

env:
  DOCKERHUB_IMAGE_PREFIX: abesesr/theses
  NAMESPACE: "api-indexation"

on:
  push:
    paths-ignore:
      - "**.md"
  workflow_dispatch:

jobs:
  build-test-pubtodockerhub:
    runs-on: ubuntu-latest
    steps:
      - name: "Build: checkout source code"
        uses: actions/checkout@v6

      - name: "Push: prepare version from git tags/branches"
        id: docker_tag_meta
        uses: docker/metadata-action@v6
        with:
          images: ${{ env.DOCKERHUB_IMAGE_PREFIX }}

      - name: "Set up Docker Buildx"
        uses: docker/setup-buildx-action@v4

      - name: "Login to DockerHub"
        uses: docker/login-action@v4
        if: >-
          github.event_name != 'pull_request' &&
          (github.ref == 'refs/heads/main' ||
          github.ref == 'refs/heads/test' ||
          github.ref == 'refs/heads/develop' ||
          startsWith(github.ref, 'refs/tags/'))
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: "Buildx api-indexation and push"
        uses: docker/build-push-action@v7
        with:
          context: .
          push: >-
            ${{ github.event_name != 'pull_request' &&
            (github.ref == 'refs/heads/main' ||
            github.ref == 'refs/heads/test' ||
            github.ref == 'refs/heads/develop' ||
            startsWith(github.ref, 'refs/tags/')) }}
          target: api-indexation-image
          tags: ${{ steps.docker_tag_meta.outputs.tags }}-${{ env.NAMESPACE }}
```

- [ ] **Step 6: Contrôler le diff et reconstruire**

Run:

```powershell
git diff --check
mvn --batch-mode clean verify
docker build `
  --target api-indexation-image `
  --tag theses-api-indexation:dev-test `
  .
git status --short
```

Expected: seuls `.dockerignore`, `Dockerfile` et le workflow sont modifiés,
et les trois validations réussissent.

- [ ] **Step 7: Commit**

```powershell
git add .dockerignore Dockerfile `
  .github/workflows/buildx-test-pubtodockerhub.yml
git commit -m "build: publier l’image Docker d’indexation"
```

---

### Task 2: Créer le rôle Elasticsearch limité dans `theses-docker`

**Repository:** `abes-esr/theses-docker`

**Files:**
- Modify: `.env-dist`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: Elasticsearch, son certificat CA et le compte `elastic`.
- Produces: le rôle `theses-noindex-writer` et le compte
  `theses-api-indexation`.

- [ ] **Step 1: Mettre à jour le dépôt et créer la branche**

Run:

```powershell
git status --short --branch
git switch develop
git pull --ff-only origin develop
git switch -c SOA-820-jobs-referencement-dev
```

Expected: branche neuve et arbre propre.

- [ ] **Step 2: Vérifier la configuration Compose de référence**

Run:

```powershell
docker compose --env-file .env-dist config --quiet
```

Expected: code de sortie `0`.

- [ ] **Step 3: Déclarer les variables du registre**

Add to `.env-dist` after the `theses-api-recherche` section:

```dotenv
######################################################
# Jobs ponctuels de référencement
######################################################
THESES_API_INDEXATION_VERSION=develop-api-indexation
THESES_API_INDEXATION_ELASTIC_USERNAME=theses-api-indexation
THESES_API_INDEXATION_ELASTIC_PASSWORD=
THESES_REFERENCEMENT_INDEX=referencement
THESES_ROBOTS_URL=https://theses.fr/robots.txt
```

- [ ] **Step 4: Transmettre les variables au setup Elasticsearch**

Add under `theses-elasticsearch-setupusers.environment`:

```yaml
THESES_API_INDEXATION_ELASTIC_USERNAME: ${THESES_API_INDEXATION_ELASTIC_USERNAME}
THESES_API_INDEXATION_ELASTIC_PASSWORD: ${THESES_API_INDEXATION_ELASTIC_PASSWORD}
THESES_REFERENCEMENT_INDEX: ${THESES_REFERENCEMENT_INDEX}
```

- [ ] **Step 5: Créer ou mettre à jour le rôle et l’utilisateur**

Insert in `theses-elasticsearch-setupusers.command`, after the creation of
the `theses-api-recherche` user and before `echo "All done!"`:

```bash
echo "Setting theses-noindex-writer role";
until curl -fsS -X PUT
  --cacert config/certs/ca/ca.crt
  -u elastic:${THESES_ELASTICSEARCH_PASSWORD}
  -H "Content-Type: application/json"
  https://theses-elasticsearch-01:${THESES_ELASTICSEARCH_HTTP_PORT}/_security/role/theses-noindex-writer
  -d "{\"cluster\":[],\"indices\":[{\"names\":[\"${THESES_REFERENCEMENT_INDEX}\"],\"privileges\":[\"read\",\"write\",\"view_index_metadata\"]}]}"
  > /dev/null;
do sleep 10; done;
echo "Setting ${THESES_API_INDEXATION_ELASTIC_USERNAME} password";
until curl -fsS -X POST
  --cacert config/certs/ca/ca.crt
  -u elastic:${THESES_ELASTICSEARCH_PASSWORD}
  -H "Content-Type: application/json"
  https://theses-elasticsearch-01:${THESES_ELASTICSEARCH_HTTP_PORT}/_security/user/${THESES_API_INDEXATION_ELASTIC_USERNAME}
  -d "{\"password\":\"${THESES_API_INDEXATION_ELASTIC_PASSWORD}\",\"enabled\":true,\"roles\":[\"theses-noindex-writer\"],\"full_name\":\"\",\"email\":\"\"}"
  > /dev/null;
do sleep 10; done;
```

When implementing inside the folded YAML scalar, keep each `curl` command on
one shell command line so `bash -c` receives valid syntax.

- [ ] **Step 6: Étendre le healthcheck**

Replace the setupusers healthcheck command with:

```yaml
"curl -fsS --cacert config/certs/ca/ca.crt -u ${THESES_API_RECHERCHE_ELASTIC_USERNAME}:${THESES_API_RECHERCHE_ELASTIC_PASSWORD} 'https://theses-elasticsearch-01:${THESES_ELASTICSEARCH_HTTP_PORT}/_search?timeout=5s' | grep -q '\"hits\"' && curl -fsS --cacert config/certs/ca/ca.crt -u ${THESES_API_INDEXATION_ELASTIC_USERNAME}:${THESES_API_INDEXATION_ELASTIC_PASSWORD} 'https://theses-elasticsearch-01:${THESES_ELASTICSEARCH_HTTP_PORT}/_security/_authenticate' | grep -q '\"username\":\"${THESES_API_INDEXATION_ELASTIC_USERNAME}\"'",
```

- [ ] **Step 7: Vérifier la configuration**

Run:

```powershell
docker compose --env-file .env-dist config --quiet
git diff --check
```

Expected: aucune erreur YAML ou d’interpolation.

---

### Task 3: Ajouter les deux jobs Docker Compose

**Repository:** `abes-esr/theses-docker`

**Files:**
- Modify: `docker-compose.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: `abesesr/theses:${THESES_API_INDEXATION_VERSION}`, Elasticsearch
  DEV, le certificat CA et les comptes créés à la tâche 2.
- Produces: `theses-referencement-init` et
  `theses-referencement-import`.

- [ ] **Step 1: Ajouter le job d’initialisation**

Add under `services` near the other API services:

```yaml
  #######################################
  # theses-referencement-init
  # Job ponctuel de création ou vérification de l'index referencement
  theses-referencement-init:
    image: abesesr/theses:${THESES_API_INDEXATION_VERSION}
    profiles:
      - referencement-jobs
    depends_on:
      theses-elasticsearch:
        condition: service_healthy
    mem_limit: ${MEM_LIMIT}
    memswap_limit: ${MEM_LIMIT}
    cpus: ${CPU_LIMIT}
    volumes:
      - ./volumes/theses-elasticsearch-setupcerts/:/app/certs/:ro
    environment:
      SPRING_PROFILES_ACTIVE: init-index
      ES_HOSTNAME: theses-elasticsearch-01
      ES_PORT: ${THESES_ELASTICSEARCH_HTTP_PORT}
      ES_PROTOCOL: https
      ES_USERNAME: elastic
      ES_PASSWORD: ${THESES_ELASTICSEARCH_PASSWORD}
      ES_CA_CERTIFICATE: /app/certs/ca/ca.crt
      REFERENCEMENT_INDEX_NAME: ${THESES_REFERENCEMENT_INDEX}
```

- [ ] **Step 2: Ajouter le job d’import**

Add immediately after the init service:

```yaml
  #######################################
  # theses-referencement-import
  # Job ponctuel d'import du robots.txt dans l'index referencement
  theses-referencement-import:
    image: abesesr/theses:${THESES_API_INDEXATION_VERSION}
    profiles:
      - referencement-jobs
    depends_on:
      theses-elasticsearch:
        condition: service_healthy
      theses-elasticsearch-setupusers:
        condition: service_healthy
    mem_limit: ${MEM_LIMIT}
    memswap_limit: ${MEM_LIMIT}
    cpus: ${CPU_LIMIT}
    volumes:
      - ./volumes/theses-elasticsearch-setupcerts/:/app/certs/:ro
    environment:
      SPRING_PROFILES_ACTIVE: import-robots
      ES_HOSTNAME: theses-elasticsearch-01
      ES_PORT: ${THESES_ELASTICSEARCH_HTTP_PORT}
      ES_PROTOCOL: https
      ES_USERNAME: ${THESES_API_INDEXATION_ELASTIC_USERNAME}
      ES_PASSWORD: ${THESES_API_INDEXATION_ELASTIC_PASSWORD}
      ES_CA_CERTIFICATE: /app/certs/ca/ca.crt
      REFERENCEMENT_INDEX_NAME: ${THESES_REFERENCEMENT_INDEX}
      ROBOTS_URL: ${THESES_ROBOTS_URL}
```

- [ ] **Step 3: Vérifier l’absence d’exposition et de démarrage automatique**

Run:

```powershell
docker compose --env-file .env-dist config --quiet
docker compose --env-file .env-dist config --services
docker compose --env-file .env-dist `
  --profile referencement-jobs `
  config --services
```

Expected:

- sans profil, aucun des deux jobs n’apparaît dans la liste ;
- avec le profil, les deux jobs apparaissent ;
- aucun bloc `ports` ou `restart` n’est présent dans les jobs.

- [ ] **Step 4: Documenter l’exploitation DEV**

Add to `README.md`:

```markdown
## Initialisation du registre de référencement

Les jobs sont ponctuels et ne démarrent pas avec la plateforme :

```bash
docker compose --profile referencement-jobs run --rm \
  theses-referencement-init

docker compose --profile referencement-jobs run --rm \
  theses-referencement-import
```

Le second job ne doit être lancé que si le premier se termine avec le code
`0`. En DEV, l’import lit `https://theses.fr/robots.txt`. Ces commandes ne
suppriment aucune directive du fichier et ne démarrent aucune API HTTP.
```

- [ ] **Step 5: Validation complète du dépôt**

Run:

```powershell
docker compose --env-file .env-dist config --quiet
docker compose --env-file .env-dist `
  --profile referencement-jobs `
  config --quiet
git diff --check
git status --short
```

Expected: seules les modifications prévues sont présentes et les deux
configurations Compose sont valides.

- [ ] **Step 6: Commit**

```powershell
git add .env-dist docker-compose.yml README.md
git commit -m "feat: ajouter les jobs DEV de référencement"
```

---

### Task 4: Publier les deux branches pour revue

**Repositories:**
- `abes-esr/theses-api-indexation`
- `abes-esr/theses-docker`

**Interfaces:**
- Consumes: les commits validés des tâches 1 à 3.
- Produces: deux PR prêtes pour revue vers `develop`.

- [ ] **Step 1: Vérifier `theses-api-indexation` avant publication**

Run:

```powershell
mvn --batch-mode clean verify
docker build `
  --target api-indexation-image `
  --tag theses-api-indexation:dev-test `
  .
git status --short --branch
git log --oneline --decorate -3
```

Expected: tests et image verts, arbre propre.

- [ ] **Step 2: Pousser et ouvrir la PR de packaging**

Create the temporary file `.soa-820-api-pr.md` with:

```markdown
## Objectif

Publier une image Docker de `theses-api-indexation` pour exécuter les jobs
ponctuels de référencement en DEV.

## Changements

- Dockerfile multiétage Java 17 ;
- exécution des tests Maven pendant la construction ;
- cible `api-indexation-image` ;
- workflow Buildx publiant `abesesr/theses:develop-api-indexation`.

Cette PR ne démarre ni n’expose l’API HTTP.

## Validation

- `mvn --batch-mode clean verify` ;
- `docker build --target api-indexation-image .`.

## Dépendance

Le lancement en DEV dépend de la PR `theses-docker` qui ajoute les deux jobs
Docker Compose et les droits Elasticsearch limités.
```

Run:

```powershell
git push -u origin SOA-820-deploiement-dev
gh pr create `
  --repo abes-esr/theses-api-indexation `
  --base develop `
  --head SOA-820-deploiement-dev `
  --title "SOA-820 : publier les jobs de référencement" `
  --body-file .soa-820-api-pr.md
gh pr ready `
  --repo abes-esr/theses-api-indexation `
  SOA-820-deploiement-dev
```

Remove `.soa-820-api-pr.md` immediately after PR creation and verify that it
does not appear in `git status`.

- [ ] **Step 3: Vérifier `theses-docker` avant publication**

Run:

```powershell
docker compose --env-file .env-dist config --quiet
docker compose --env-file .env-dist `
  --profile referencement-jobs `
  config --quiet
git status --short --branch
git log --oneline --decorate -3
```

Expected: configuration valide et arbre propre.

- [ ] **Step 4: Pousser et ouvrir la PR de déploiement**

Create the temporary file `.soa-820-docker-pr.md` with:

```markdown
## Objectif

Exécuter ponctuellement en DEV la création de l’index `referencement` puis
l’import du `robots.txt`.

## Changements

- rôle Elasticsearch `theses-noindex-writer` limité à `referencement` ;
- compte applicatif `theses-api-indexation` ;
- job manuel `theses-referencement-init` avec le compte administrateur ;
- job manuel `theses-referencement-import` avec le compte limité ;
- profil Compose `referencement-jobs` ;
- aucun port et aucun redémarrage automatique.

## Ordre d’exécution

1. `theses-referencement-init` ;
2. contrôle du code de sortie ;
3. `theses-referencement-import` ;
4. validation des compteurs et des trois types de page.

## Validation

- `docker compose --env-file .env-dist config --quiet` ;
- `docker compose --env-file .env-dist --profile referencement-jobs config
  --quiet`.
```

Run:

```powershell
git push -u origin SOA-820-jobs-referencement-dev
gh pr create `
  --repo abes-esr/theses-docker `
  --base develop `
  --head SOA-820-jobs-referencement-dev `
  --title "SOA-820 : ajouter les jobs DEV de référencement" `
  --body-file .soa-820-docker-pr.md
gh pr ready `
  --repo abes-esr/theses-docker `
  SOA-820-jobs-referencement-dev
```

Remove `.soa-820-docker-pr.md` immediately after PR creation and verify that
it does not appear in `git status`.

- [ ] **Step 5: Contrôler les PR**

Run:

```powershell
gh pr checks `
  --repo abes-esr/theses-api-indexation `
  SOA-820-deploiement-dev
gh pr checks `
  --repo abes-esr/theses-docker `
  SOA-820-jobs-referencement-dev
```

Expected: tous les contrôles disponibles sont verts. Do not merge with an
administrative bypass unless the user explicitly authorizes it for these
new PRs.

---

### Task 5: Vérifier la publication de l’image

**Repositories:**
- `abes-esr/theses-api-indexation`
- `abes-esr/theses-docker`

**Interfaces:**
- Consumes: les deux PR fusionnées dans `develop`.
- Produces: l’image `abesesr/theses:develop-api-indexation` disponible pour
  DEV.

- [ ] **Step 1: Vérifier les commits de fusion**

Run:

```powershell
gh pr view --repo abes-esr/theses-api-indexation `
  SOA-820-deploiement-dev `
  --json state,mergedAt,mergeCommit,url
gh pr view --repo abes-esr/theses-docker `
  SOA-820-jobs-referencement-dev `
  --json state,mergedAt,mergeCommit,url
```

Expected: les deux états valent `MERGED`.

- [ ] **Step 2: Vérifier le workflow de construction**

Run:

```powershell
gh run list `
  --repo abes-esr/theses-api-indexation `
  --workflow buildx-test-pubtodockerhub.yml `
  --branch develop `
  --limit 3
```

Expected: le dernier run est `completed/success`.

- [ ] **Step 3: Vérifier le manifeste Docker**

Run:

```powershell
docker manifest inspect abesesr/theses:develop-api-indexation
```

Expected: manifeste disponible sans erreur.

---

### Task 6: Localiser et préparer la pile `theses-docker` DEV

**Environment:** SSH DEV uniquement.

**Interfaces:**
- Consumes: les dépôts fusionnés et l’image publiée.
- Produces: une pile DEV à jour, avec un `.env` contenant les variables
  requises sans divulgation de secret.

- [ ] **Step 1: Identifier le serveur DEV canonique**

On each available DEV connector, run only:

```bash
hostname
test -d /opt/pod/theses-docker &&
  git -C /opt/pod/theses-docker remote get-url origin
```

Accept a host only if the remote is exactly:

```text
https://github.com/abes-esr/theses-docker.git
```

Also require:

```bash
grep -q '^OTEL_ENVIRONMENT=dev$' /opt/pod/theses-docker/.env
```

Do not print `.env`. `diplotaxis5-dev.v212.abes.fr` has already been checked
and does not host this stack.

If no accessible host satisfies all checks, stop remote execution and report
the infrastructure blocker. Do not guess another path or environment.

- [ ] **Step 2: Vérifier que le checkout distant est propre**

Run remotely:

```bash
cd /opt/pod/theses-docker
git status --short --branch
git remote get-url origin
```

Expected: no local changes and the expected GitHub remote. Stop if the
worktree is dirty.

- [ ] **Step 3: Mettre à jour le checkout**

Run remotely:

```bash
cd /opt/pod/theses-docker
git switch develop
git pull --ff-only origin develop
```

Expected: local `develop` equals `origin/develop`.

- [ ] **Step 4: Ajouter les variables DEV sans afficher le secret**

Run remotely from `/opt/pod/theses-docker`:

```bash
set -euo pipefail
umask 077

upsert_env() {
  key="$1"
  value="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

upsert_env THESES_API_INDEXATION_VERSION develop-api-indexation
upsert_env THESES_API_INDEXATION_ELASTIC_USERNAME theses-api-indexation
upsert_env THESES_REFERENCEMENT_INDEX referencement
upsert_env THESES_ROBOTS_URL https://theses.fr/robots.txt

if ! grep -Eq '^THESES_API_INDEXATION_ELASTIC_PASSWORD=.+$' .env; then
  generated_secret="$(openssl rand -hex 32)"
  upsert_env THESES_API_INDEXATION_ELASTIC_PASSWORD "$generated_secret"
  unset generated_secret
fi
```

The command must return no secret value.

- [ ] **Step 5: Vérifier uniquement la présence des clés**

Run remotely:

```bash
cd /opt/pod/theses-docker
for key in \
  THESES_API_INDEXATION_VERSION \
  THESES_API_INDEXATION_ELASTIC_USERNAME \
  THESES_API_INDEXATION_ELASTIC_PASSWORD \
  THESES_REFERENCEMENT_INDEX \
  THESES_ROBOTS_URL
do
  grep -Eq "^${key}=.+$" .env || exit 1
done
docker compose --env-file .env config --quiet
docker compose --env-file .env \
  --profile referencement-jobs \
  config --quiet
```

Expected: code `0` and no environment value printed.

---

### Task 7: Exécuter `init-index` et `import-robots` en DEV

**Environment:** the canonical `theses-docker` DEV host from Task 6.

**Interfaces:**
- Consumes: image publiée, Elasticsearch DEV sain, `.env` validé.
- Produces: index `referencement` alimenté depuis le `robots.txt` réel.

- [ ] **Step 1: Récupérer l’image**

Run remotely:

```bash
cd /opt/pod/theses-docker
docker compose --env-file .env \
  --profile referencement-jobs \
  pull theses-referencement-init theses-referencement-import
```

Expected: image `develop-api-indexation` téléchargée.

- [ ] **Step 2: Recréer le setup des comptes**

Run remotely:

```bash
cd /opt/pod/theses-docker
docker compose --env-file .env up -d \
  --force-recreate theses-elasticsearch-setupusers
docker compose --env-file .env ps theses-elasticsearch-setupusers
```

Poll `docker compose ps` for at most 20 minutes. Continue only when the
container is `healthy`.

- [ ] **Step 3: Exécuter l’initialisation**

Run remotely:

```bash
cd /opt/pod/theses-docker
set -o pipefail
docker compose --env-file .env \
  --profile referencement-jobs \
  run --rm theses-referencement-init \
  2>&1 | tee /tmp/soa-820-init-index.log
```

Expected:

- code de sortie `0` ;
- log indiquant que l’index a été créé ou que son mapping est compatible.

Stop immediately on any other result.

- [ ] **Step 4: Exécuter le premier import**

Run remotely:

```bash
cd /opt/pod/theses-docker
set -o pipefail
docker compose --env-file .env \
  --profile referencement-jobs \
  run --rm theses-referencement-import \
  2>&1 | tee /tmp/soa-820-import-robots.log
```

Expected:

- code de sortie `0` ;
- `invalides=0` ;
- `créés + existants = valides`.

- [ ] **Step 5: Vérifier les types et les métadonnées**

Run a `_search` on `referencement` with the dedicated credentials and this
body:

```json
{
  "size": 0,
  "query": {
    "term": {
      "noIndex": true
    }
  },
  "aggs": {
    "par_type": {
      "terms": {
        "field": "pageType",
        "size": 3
      },
      "aggs": {
        "echantillon": {
          "top_hits": {
            "size": 2,
            "_source": [
              "pageType",
              "noIndex",
              "demandeRef",
              "updatedBy",
              "updatedAt"
            ]
          }
        }
      }
    }
  }
}
```

Execute the request from
`theses-elasticsearch-setupusers`, using its existing environment variables
and `config/certs/ca/ca.crt`. Do not place credentials in the command line or
print them.

Expected:

- three buckets: `THESE_SOUTENUE`, `PERSONNE`,
  `THESE_EN_PREPARATION`;
- at least two hits per bucket;
- every hit has `noIndex: true`;
- imported documents have `demandeRef: IMPORT-ROBOTS-INITIAL` and
  `updatedBy: robots.txt-importer`.

- [ ] **Step 6: Vérifier la reprise idempotente**

Run the import job a second time:

```bash
cd /opt/pod/theses-docker
set -o pipefail
docker compose --env-file .env \
  --profile referencement-jobs \
  run --rm theses-referencement-import \
  2>&1 | tee /tmp/soa-820-import-robots-reprise.log
```

Expected:

- code de sortie `0` ;
- `créés=0` ;
- `existants=valides` ;
- `invalides=0`.

- [ ] **Step 7: Vérifier l’absence de service persistant**

Run remotely:

```bash
docker ps -a --format '{{.Names}}' |
  grep -E 'theses-referencement-(init|import)' &&
  exit 1 || true
```

Expected: aucun conteneur de job restant.

- [ ] **Step 8: Produire le bilan**

Record:

- the two merge commits;
- the Docker image digest;
- the init job result;
- the first and second import counters;
- two identifiers from each page type;
- the confirmation that no HTTP port was exposed;
- the confirmation that the production `robots.txt` was not modified.

Do not include credentials, tokens, CA contents or direct personal data.

---

## Completion Criteria

- The two implementation PRs are merged into `develop`.
- `abesesr/theses:develop-api-indexation` is published.
- The DEV Compose configuration is valid with and without
  `referencement-jobs`.
- The Elasticsearch account has no cluster privilege and is limited to
  `referencement`.
- `init-index` exits successfully.
- `import-robots` exits successfully with zero invalid line.
- A second import creates no document.
- NNT, PPN and subject-number samples exist with `noIndex: true`.
- No API service or port is left running.
- No TEST or PROD system and no production `robots.txt` file is modified.
