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
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "agent@abes.fr"
}
```

Les types acceptés sont :

- `THESE_SOUTENUE` avec un NNT, par exemple `2024AIXM0640` ;
- `PERSONNE` avec un PPN, par exemple `270350292` ;
- `THESE_EN_PREPARATION` avec un numéro de sujet, par exemple `s233841`.

Une réactivation utilise le même endpoint avec `noIndex: false`. Le document
Elasticsearch est conservé.

L’API doit rester sur le réseau interne. Les paramètres Elasticsearch sont
fournis par `ES_HOSTNAME`, `ES_PORT`, `ES_PROTOCOL`, `ES_USERNAME`,
`ES_PASSWORD` et `ES_CA_CERTIFICATE`.
