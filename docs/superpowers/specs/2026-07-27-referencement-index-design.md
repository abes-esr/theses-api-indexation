# SOA-820 — Conception de l’initialisation de l’index `referencement`

## Objectif

Créer et contrôler automatiquement l’index Elasticsearch `referencement`,
sans opération manuelle dans Kibana et sans donner à l’API en fonctionnement
normal le droit de créer ou supprimer un index.

Cette spécification couvre uniquement la première tranche de SOA-820 :

- mapping de l’index ;
- initialisation idempotente ;
- vérification du mapping ;
- séparation des droits Elasticsearch.

Le contrat HTTP d’écriture, l’activation/désactivation et l’import du
`robots.txt` seront spécifiés dans les tranches suivantes.

## Document Elasticsearch

L’identifiant Elasticsearch `_id` est l’identifiant canonique de la page :

- NNT, par exemple `2024AIXM0640` ;
- PPN, par exemple `123456789` ;
- numéro de sujet, par exemple `s123456`.

Le document possède les champs suivants :

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "prenom.nom@abes.fr",
  "updatedAt": "2026-07-27T10:15:30Z"
}
```

`pageType` accepte les valeurs :

```text
THESE_SOUTENUE
THESE_EN_PREPARATION
PERSONNE
```

L’index ne contient ni motif libre, ni justificatif d’identité.

## Mapping versionné

Le mapping est stocké dans :

```text
src/main/resources/indexs/referencement.json
```

Contenu :

```json
{
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "pageType": {
        "type": "keyword"
      },
      "noIndex": {
        "type": "boolean"
      },
      "demandeRef": {
        "type": "keyword",
        "ignore_above": 64
      },
      "updatedBy": {
        "type": "keyword",
        "ignore_above": 255
      },
      "updatedAt": {
        "type": "date"
      }
    }
  }
}
```

Le mode `dynamic: strict` refuse tout champ qui n’est pas prévu par le
contrat.

Le nombre de shards et de réplicas n’est pas imposé par l’application. Il
reste piloté par la configuration Elasticsearch de chaque environnement.

## Mode d’initialisation

La même image Docker est utilisée dans deux modes.

### Profil `init-index`

Le profil Spring `init-index` exécute un processus ponctuel :

1. interroger Elasticsearch pour savoir si `referencement` existe ;
2. si l’index est absent, le créer avec le mapping versionné ;
3. si l’index existe, contrôler les champs attendus, leurs types et
   `dynamic: strict` ;
4. terminer avec le code `0` lorsque l’index est utilisable ;
5. terminer en erreur si le mapping existant est incompatible.

Le processus ne supprime jamais l’index et ne remplace jamais son mapping.

Si plusieurs initialiseurs démarrent simultanément et que l’un d’eux crée
l’index entre le contrôle d’existence et la création, l’erreur
`resource_already_exists_exception` est suivie d’une nouvelle vérification du
mapping. Un mapping compatible produit alors un succès.

### Profil normal

Le profil normal démarre l’API HTTP. Il ne contient aucun initialiseur
d’index et ne tente jamais de modifier le mapping.

## Orchestration

`theses-docker` exécutera d’abord un service ponctuel utilisant le profil
`init-index`. Le service HTTP dépendra de sa réussite avec
`condition: service_completed_successfully`.

Un déploiement ne demande donc aucune commande Elasticsearch manuelle :

1. Elasticsearch démarre ;
2. les comptes techniques sont créés ;
3. le job `init-index` crée ou vérifie l’index ;
4. le job s’arrête ;
5. l’API démarre.

## Droits Elasticsearch

Le compte d’initialisation possède uniquement, sur `referencement` :

```text
create_index
view_index_metadata
```

Le compte de l’API possède uniquement :

```text
read
write
view_index_metadata
```

Aucun compte applicatif ne reçoit `delete_index`.

## Gestion des erreurs

- Elasticsearch indisponible : le job échoue et l’API ne démarre pas.
- Mapping embarqué illisible : le job échoue avant tout appel de création.
- Index absent et création refusée : le job échoue avec une erreur explicite.
- Index existant et compatible : succès sans écriture.
- Index existant et incompatible : échec du déploiement, sans modification.
- Course entre deux initialiseurs : nouvelle lecture et succès si compatible.

Les erreurs doivent nommer l’index et le champ incompatible sans afficher
d’identifiant ni de mot de passe Elasticsearch.

## Stratégie de test

Le premier cycle TDD porte sur le comportement principal :

```text
index absent → création à partir du mapping versionné
```

Les cycles suivants couvrent :

1. index existant et compatible → aucune création ;
2. champ manquant ou type incompatible → échec ;
3. `dynamic` différent de `strict` → échec ;
4. concurrence de création → relecture puis succès ;
5. Elasticsearch indisponible → échec du job ;
6. profil normal → initialiseur non chargé.

Les tests unitaires utilisent une abstraction du client Elasticsearch. Un
test d’intégration avec Testcontainers et Elasticsearch 8.10.3 vérifie ensuite
la création réelle et le mapping obtenu.

## Risques de régression couverts

- perte des décisions par suppression/recréation de l’index ;
- démarrage de l’API avec un mapping incompatible ;
- création concurrente lors d’un déploiement à plusieurs instances ;
- dérive silencieuse du schéma ;
- écriture de champs non autorisés ;
- exposition de privilèges Elasticsearch trop larges ;
- dépendance à une intervention manuelle non reproductible.
