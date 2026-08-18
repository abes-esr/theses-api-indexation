# theses-api-indexation
Cette API fournit des services permettant d'indexer les thèses et leurs métadonnées dans le moteur d'indexation.

## Écriture du référencement

L’API interne expose :

```http
PUT /api/v1/referencements/{identifiant}
Content-Type: application/json
```

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ABESSTP-12345"
}
```

L’auteur de la modification n’est jamais accepté dans le JSON. Il est produit
par le serveur à partir de l’en-tête Shibboleth `eppn`. Cet ePPN doit figurer
dans la liste fermée `THESES_NOINDEX_ALLOWED_EPPNS` (valeurs séparées par des
virgules). Une liste vide refuse toutes les écritures. L’absence d’identité
retourne `401` et une identité non autorisée retourne `403`.

Les types acceptés sont :

- `THESE_SOUTENUE` avec un NNT, par exemple `2024AIXM0640` ;
- `PERSONNE` avec un PPN, par exemple `270350292` ;
- `THESE_EN_PREPARATION` avec un numéro de sujet, par exemple `s233841`.

Une réactivation utilise le même endpoint avec `noIndex: false`. Le document
Elasticsearch est conservé.

L’API doit rester sur le réseau interne. Les paramètres Elasticsearch sont
fournis par `ES_HOSTNAME`, `ES_PORT`, `ES_PROTOCOL`, `ES_USERNAME`,
`ES_PASSWORD` et `ES_CA_CERTIFICATE`.

Le service expose uniquement `GET /actuator/health` pour son healthcheck
Docker. Cette santé vérifie que le processus HTTP répond, sans publier de
détails internes ; l’accès Elasticsearch est contrôlé séparément lors de la
recette et par les appels métier.

## Import initial du robots.txt

L’import ponctuel lit par défaut `https://theses.fr/robots.txt`, crée les
décisions absentes avec `noIndex: true`, puis s’arrête :

```powershell
java -jar target/theses-api-indexation-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=import-robots
```

L’index `referencement` doit exister avant l’import. Le profil réutilise les
variables Elasticsearch `ES_HOSTNAME`, `ES_PORT`, `ES_PROTOCOL`,
`ES_USERNAME`, `ES_PASSWORD` et `ES_CA_CERTIFICATE`.

La source et les délais peuvent être adaptés avec :

- `ROBOTS_URL`, par défaut `https://theses.fr/robots.txt` ;
- `ROBOTS_CONNECT_TIMEOUT`, par défaut `5s` ;
- `ROBOTS_READ_TIMEOUT`, par défaut `30s`.

Le job n’écrase jamais un document existant. Après un échec partiel, il peut
être relancé : les documents déjà créés sont comptés comme existants et les
documents restants sont importés.
