# Jobs DEV d’initialisation du référencement

## Contexte

SOA-12 remplace progressivement les exclusions individuelles du
`robots.txt` par des directives `noindex`. Le dépôt
`theses-api-indexation` contient désormais le registre Elasticsearch
`referencement`, le contrat d’écriture et l’importeur de l’ancien
`robots.txt`.

Cette tranche valide uniquement la couche de données en environnement DEV.
Elle ne constitue pas encore le déréférencement fonctionnel de bout en bout :
le front SSR, le sitemap et les exports devront consommer le registre avant
que les exclusions individuelles puissent être retirées du `robots.txt`.

## Objectif

Construire et publier une image Docker de `theses-api-indexation`, l’intégrer
à `theses-docker` sous la forme de deux jobs manuels, puis :

1. créer ou vérifier l’index `referencement` en DEV ;
2. importer le contenu courant de `https://theses.fr/robots.txt` dans cet
   index ;
3. vérifier la présence de NNT, de PPN et de numéros de sujet avec
   `noIndex: true`.

## Périmètre

### Inclus

- le packaging Docker de `theses-api-indexation` ;
- la publication de l’image Docker pour la branche `develop` ;
- un compte Elasticsearch limité au registre `referencement` ;
- un job manuel `init-index` ;
- un job manuel `import-robots` ;
- les contrôles DEV de mapping, de compteurs et d’échantillons.

### Exclus

- le démarrage permanent de l’API HTTP ;
- l’exposition d’un port ou d’une route de reverse proxy ;
- l’authentification Shibboleth de l’API d’administration ;
- la lecture publique du registre par `theses-api-recherche` ;
- la balise HTML `noindex` dans `theses-front` ;
- le filtrage du sitemap dans `theses-seo` ;
- les en-têtes `X-Robots-Tag` dans `theses-api-export` ;
- toute modification du `robots.txt` de production ;
- toute exécution en TEST ou en PROD.

## Dépôts concernés

### `abes-esr/theses-api-indexation`

Le dépôt fournit :

- un `Dockerfile` multiétage basé sur Java 17 ;
- un workflow GitHub Actions qui exécute `mvn --batch-mode clean verify`
  avant la construction de l’image ;
- une étape Maven dans le Dockerfile qui produit le JAR avec
  `-DskipTests`, sans réexécuter les tests Testcontainers ;
- une image d’exécution JRE 17 ;
- une cible Docker nommée `api-indexation-image` ;
- un workflow GitHub Actions aligné sur celui de
  `theses-api-recherche`.

Pour un push sur `develop`, l’image publiée est :

```text
abesesr/theses:develop-api-indexation
```

Le conteneur conserve le point d’entrée standard de l’application. Le profil
Spring est fourni par la configuration Docker Compose de chaque job.

### `abes-esr/theses-docker`

Le dépôt fournit deux services sous le profil Docker Compose
`referencement-jobs` :

- `theses-referencement-init` ;
- `theses-referencement-import`.

Ces services :

- utilisent la même image `theses-api-indexation` ;
- n’exposent aucun port ;
- ne déclarent aucune politique de redémarrage ;
- montent les certificats Elasticsearch en lecture seule ;
- se terminent à la fin du job ;
- ne sont jamais lancés par un `docker compose up` normal.

## Configuration

Les variables non secrètes ajoutées à `.env-dist` sont :

```text
THESES_API_INDEXATION_VERSION=develop-api-indexation
THESES_REFERENCEMENT_INDEX=referencement
THESES_ROBOTS_URL=https://theses.fr/robots.txt
THESES_API_INDEXATION_ELASTIC_USERNAME=theses-api-indexation
```

Le secret suivant est documenté sans valeur réelle :

```text
THESES_API_INDEXATION_ELASTIC_PASSWORD=
```

Les deux jobs utilisent également les variables Elasticsearch déjà présentes
dans `theses-docker`, dont :

```text
THESES_ELASTICSEARCH_HTTP_PORT
THESES_ELASTICSEARCH_PASSWORD
```

Le certificat CA est monté depuis le volume existant
`volumes/theses-elasticsearch-setupcerts` et exposé à l’application par
`ES_CA_CERTIFICATE`.

## Droits Elasticsearch

Le job `theses-elasticsearch-setupusers` crée ou met à jour le rôle :

```text
theses-noindex-writer
```

Ce rôle n’accorde aucun privilège de cluster. Sur l’index
`referencement`, il accorde uniquement :

```text
read
write
view_index_metadata
```

Le compte `theses-api-indexation` reçoit exclusivement ce rôle.

Le job `theses-referencement-init` utilise le compte administrateur
Elasticsearch DEV, car il doit pouvoir créer l’index. Le mot de passe reste
dans le fichier `.env` du serveur et n’est ni passé en argument de commande,
ni enregistré dans Git.

Le job `theses-referencement-import` utilise le compte limité
`theses-api-indexation`.

## Flux d’exécution

### Précontrôles

L’exécution distante est autorisée uniquement sur le serveur qui héberge la
pile DEV issue de `abes-esr/theses-docker`. Le serveur
`diplotaxis5-dev.v212.abes.fr` ne contient pas cette pile et ne doit donc pas
être utilisé pour ces jobs.

Avant toute exécution :

1. l’image `develop-api-indexation` doit être disponible ;
2. `docker compose --env-file .env config --quiet` doit réussir ;
3. Elasticsearch DEV doit être sain ;
4. le service `theses-elasticsearch-setupusers` doit avoir créé le compte
   limité ;
5. aucune commande ne doit cibler TEST ou PROD.

### Initialisation

Commande logique :

```bash
docker compose --profile referencement-jobs run --rm \
  theses-referencement-init
```

Le job active le profil Spring `init-index`. Il crée l’index lorsqu’il est
absent ou vérifie strictement son mapping lorsqu’il existe, puis termine.

Un code de sortie différent de zéro arrête la procédure. Aucun import ne doit
être lancé après un échec d’initialisation.

### Import

Commande logique :

```bash
docker compose --profile referencement-jobs run --rm \
  theses-referencement-import
```

Le job active le profil Spring `import-robots` et télécharge
`https://theses.fr/robots.txt`. Il crée uniquement les décisions absentes et
n’écrase jamais un document existant.

Le rapport doit afficher :

- le nombre total de lignes ;
- le nombre d’identifiants valides ;
- les doublons ;
- les lignes ignorées ;
- les lignes invalides ;
- les documents créés ;
- les documents déjà existants.

Le nombre exact d’identifiants peut évoluer avec le fichier de production.
Les invariants sont :

```text
invalides = 0
créés + existants = valides
```

## Gestion des erreurs et reprise

- Une indisponibilité HTTP du `robots.txt` arrête le job avant toute écriture.
- Un mapping incompatible arrête `init-index` sans supprimer ni recréer
  l’index.
- Une erreur Elasticsearch pendant l’import produit un rapport partiel et un
  code de sortie non nul.
- Une relance est sûre : les documents déjà créés sont comptés comme
  existants et ne sont pas remplacés.
- L’index DEV n’est jamais supprimé automatiquement, y compris après un échec.
- Les services ponctuels sont supprimés après leur exécution grâce à
  `docker compose run --rm`.

## Validation

### Validation locale

Dans `theses-api-indexation` :

```bash
mvn --batch-mode clean verify
docker build --target api-indexation-image \
  --tag theses-api-indexation:dev-test \
  .
docker image inspect theses-api-indexation:dev-test
```

La vérification Maven et le packaging Docker sont deux étapes distinctes :
les tests, notamment ceux fondés sur Testcontainers, s’exécutent avant le
build Docker ; le build produit ensuite le JAR sans les réexécuter.

Dans `theses-docker` :

```bash
docker compose --env-file .env-dist config --quiet
```

### Validation DEV

Après les deux jobs :

1. l’index `referencement` existe ;
2. son mapping est strict et compatible avec la ressource versionnée ;
3. le rapport d’import respecte les invariants ;
4. au moins deux NNT, deux PPN et deux numéros de sujet sont relus ;
5. chaque document relu contient `noIndex: true` ;
6. les métadonnées d’audit indiquent l’import initial ;
7. aucun conteneur de job ne reste actif ;
8. aucune route HTTP de `theses-api-indexation` n’est exposée.

## Limite fonctionnelle

Cette validation prouve que le registre peut être créé et alimenté en DEV.
Elle ne prouve pas encore qu’une page est déréférencée par Google, Bing,
Google Scholar ou Brave.

Les directives `Disallow` individuelles doivent rester en production jusqu’à
ce que :

1. le front rende la balise `noindex` côté serveur ;
2. le sitemap exclue les pages concernées ;
3. les exports portent `X-Robots-Tag: noindex` ;
4. la recette end-to-end confirme que les robots peuvent explorer les pages
   et lire la directive `noindex`.
