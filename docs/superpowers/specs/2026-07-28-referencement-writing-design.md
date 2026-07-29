# SOA-820 — Contrat d’écriture du référencement

## Objectif

Cette spécification couvre la deuxième tranche de SOA-820 :

- exposer un contrat HTTP interne pour activer ou désactiver `noIndex` ;
- enregistrer la décision dans l’index Elasticsearch `referencement` ;
- valider les NNT, PPN et numéros de sujet ;
- fournir au futur importeur du `robots.txt` le même service d’écriture.

La création et la vérification de l’index sont déjà prises en charge par le
profil ponctuel `init-index`.

## Fonctionnement cible

L’écriture est exposée par une API interne. Dans cette tranche, elle n’est
appelée directement ni par ABESstp ni par un navigateur public.

```text
Application interne
        |
        | PUT /api/v1/referencements/{identifiant}
        v
Contrôleur HTTP
        |
        v
Service de référencement
        |
        v
Gateway documentaire Elasticsearch
        |
        v
Index referencement
```

Une future interface d’administration pourra utiliser ce contrat sans modifier
le service métier.

## Contrat HTTP

### Requête

```http
PUT /api/v1/referencements/{identifiant}
Content-Type: application/json
```

Exemple d’activation de la désindexation :

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "agent@abes.fr"
}
```

Exemple de réactivation de l’indexation :

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": false,
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "agent@abes.fr"
}
```

`updatedAt` n’est jamais fourni par l’appelant. Il est produit par le serveur à
partir d’une horloge UTC.

### Réponse

Une création et une mise à jour réussies retournent toutes les deux
`200 OK`. Cette réponse uniforme facilite les appels idempotents.

```json
{
  "id": "2024AIXM0640",
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "agent@abes.fr",
  "updatedAt": "2026-07-28T09:15:30Z"
}
```

Répéter exactement la même requête est autorisé. Si `pageType`, `noIndex`,
`demandeRef` et `updatedBy` sont inchangés, le service ne réécrit pas le
document et conserve sa valeur `updatedAt`. Le contrat `PUT` est ainsi
réellement idempotent.

Une modification d’au moins une de ces valeurs provoque une nouvelle écriture
et produit une nouvelle valeur `updatedAt`.

## Document Elasticsearch

L’identifiant présent dans l’URL devient directement le `_id` du document.

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ABESSTP-12345",
  "updatedBy": "agent@abes.fr",
  "updatedAt": "2026-07-28T09:15:30Z"
}
```

La désactivation de `noIndex` ne supprime jamais le document. Elle remplace sa
valeur par `false` et actualise les métadonnées.

Cette décision permet de distinguer :

- une page qui n’a jamais fait l’objet d’une décision ;
- une page dont la désindexation est active ;
- une page dont la désindexation a été annulée.

Le modèle ne constitue pas un historique complet : il conserve uniquement le
dernier état et les métadonnées de la dernière écriture.

## Validation

Tous les champs du corps sont obligatoires.

### Type de page et identifiant

| `pageType` | Nature | Format canonique | Exemple |
|---|---|---|---|
| `THESE_SOUTENUE` | NNT | `[0-9]{4}[A-Z0-9]{8}` | `2024AIXM0640` |
| `PERSONNE` | PPN | `[0-9]{8}[0-9X]` | `270350292` |
| `THESE_EN_PREPARATION` | Numéro de sujet | `s[0-9]+` | `s233841` |

Le format est strict :

- le NNT utilise des majuscules ;
- le caractère de contrôle `X` d’un PPN est en majuscule ;
- le préfixe du numéro de sujet est un `s` minuscule ;
- aucun espace avant ou après l’identifiant n’est accepté ;
- le format de l’identifiant doit correspondre à `pageType`.

Cette tranche vérifie le format du PPN, mais ne recalcule pas sa clé de
contrôle.

### Métadonnées

- `noIndex` est un booléen obligatoire ;
- `demandeRef` est une chaîne non vide de 64 caractères au maximum ;
- `updatedBy` est une chaîne non vide de 255 caractères au maximum ;
- aucun champ JSON supplémentaire n’est accepté.

Une requête invalide ne provoque aucun appel à Elasticsearch.

## Erreurs HTTP

Les erreurs utilisent `application/problem+json`.

### Erreur de validation

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "about:blank",
  "title": "Requête de référencement invalide",
  "status": 400,
  "detail": "L’identifiant s233841 est incompatible avec le type THESE_SOUTENUE",
  "instance": "/api/v1/referencements/s233841"
}
```

### Elasticsearch indisponible ou écriture refusée

Une indisponibilité technique d’Elasticsearch retourne `503 Service
Unavailable`. Le détail nomme l’identifiant et l’opération, sans exposer
d’identifiant technique, de mot de passe ou de certificat.

Une erreur applicative inattendue retourne `500 Internal Server Error`.

L’authentification et les réponses `401` ou `403` seront définies par
l’infrastructure qui exposera l’API interne. Elles ne sont pas implémentées dans
SOA-820.

## Composants

### Contrôleur HTTP

Le contrôleur :

- reçoit et désérialise la requête ;
- applique les contraintes de structure ;
- transmet une commande au service ;
- transforme le résultat en réponse HTTP ;
- transforme les erreurs de validation en `ProblemDetail`.

Il ne contient aucun code Elasticsearch.

### Service de référencement

Le service :

- vérifie la cohérence entre `pageType` et l’identifiant ;
- recherche l’éventuel document existant ;
- retourne directement ce document lorsque la requête est strictement
  identique ;
- produit `updatedAt` avec une `Clock` injectable ;
- construit le document à enregistrer ;
- appelle le gateway documentaire ;
- retourne l’état effectivement demandé.

La `Clock` injectable garantit des tests déterministes.

### Gateway documentaire

Un nouveau gateway est consacré à la lecture et à l’écriture des documents. Il
reste séparé du gateway existant, responsable du cycle de vie de l’index.

Le gateway documentaire fournit trois opérations :

- rechercher un document par son `_id` ;
- enregistrer ou remplacer un document avec un `_id` explicite ;
- créer un document uniquement s’il est absent, pour la migration initiale.

L’enregistrement remplace entièrement le dernier document. La création
conditionnelle utilise la sémantique Elasticsearch `create`, afin qu’une
décision existante ne soit jamais écrasée par l’importeur.

### Configuration Elasticsearch

La construction sécurisée du client Elasticsearch est partagée entre :

- le profil `init-index`, qui crée ou vérifie l’index ;
- le profil normal, qui expose l’API et écrit les documents.

Le job `init-index` reste ponctuel. Le profil normal ne reçoit jamais les droits
`create_index` ou `delete_index`.

## Import du `robots.txt`

L’importeur sera traité dans une tranche suivante de SOA-820. Il s’agit d’une
migration ponctuelle, lancée explicitement, et non d’une synchronisation
récurrente.

Il appellera directement le service de référencement, sans passer par HTTP.

Pour chaque ligne reconnue, il écrira :

```json
{
  "noIndex": true,
  "demandeRef": "IMPORT-ROBOTS-INITIAL",
  "updatedBy": "robots.txt-importer"
}
```

Le `pageType` sera déduit du format :

- NNT → `THESE_SOUTENUE` ;
- PPN → `PERSONNE` ;
- numéro commençant par `s` → `THESE_EN_PREPARATION`.

Le partage du service garantit que l’API et l’importeur appliquent les mêmes
validations et produisent le même document.

L’importeur crée uniquement les documents absents. Lorsqu’un identifiant existe
déjà, il conserve la décision présente, notamment un éventuel
`noIndex: false`. Cette règle rend une relance de la migration sans danger et
empêche le contenu historique du `robots.txt` de réactiver une désindexation
annulée dans l’API.

Une ligne en double est ignorée après sa première occurrence. Une ligne
invalide est signalée dans le bilan d’import sans être écrite.

## Concurrence

Le volume d’écriture est faible et aucun verrou métier n’est ajouté dans cette
tranche.

Deux écritures simultanées sur le même identifiant suivent la règle
« dernière écriture acceptée par Elasticsearch ». Le document reste toujours
complet, car chaque appel remplace l’ensemble de ses champs.

Un historique exhaustif ou un contrôle de version optimiste pourra être ajouté
ultérieurement si le besoin métier apparaît.

## Sécurité

L’API est destinée au réseau interne et ne doit pas être publiée directement
sur Internet.

Dans SOA-820 :

- `updatedBy` est fourni par l’application interne appelante ;
- l’application appelante est considérée comme fiable ;
- le mot de passe Elasticsearch et le certificat CA restent fournis par
  secrets et montage de fichier ;
- aucun secret n’est accepté dans le corps HTTP ni écrit dans les journaux.

Une future interface authentifiée pourra remplacer `updatedBy` par l’identité
de l’utilisateur connecté sans modifier le service ni le document
Elasticsearch.

## Stratégie de test

Le développement suit des cycles TDD courts.

### Service

1. activation d’un NNT avec `noIndex: true` ;
2. réactivation du même NNT avec `noIndex: false` ;
3. écriture d’un PPN ;
4. écriture d’un numéro de sujet ;
5. rejet d’une combinaison identifiant/type incohérente ;
6. production déterministe de `updatedAt` ;
7. répétition d’une requête identique sans nouvelle écriture ni modification
   de `updatedAt`.

### Contrôleur

1. requête valide → `200 OK` et état complet ;
2. champ manquant → `400 Bad Request` ;
3. champ inconnu → `400 Bad Request` ;
4. identifiant invalide → `400 Bad Request` ;
5. Elasticsearch indisponible → `503 Service Unavailable`.

### Intégration Elasticsearch

1. création réelle d’un document NNT ;
2. remplacement du document avec `noIndex: false` ;
3. création réelle d’un document PPN ;
4. création réelle d’un document de sujet ;
5. vérification de l’absence de champs non prévus.

## Risques de régression

- exposition accidentelle de l’API sans protection réseau ;
- acceptation d’un identifiant dans le mauvais type de page ;
- valeur `updatedAt` contrôlée par l’appelant ;
- suppression du document lors d’une réactivation et perte de la dernière
  décision ;
- divergence entre les règles de l’API et celles de l’importeur ;
- attribution au profil normal de droits Elasticsearch réservés à
  `init-index` ;
- régression du job ponctuel lors du partage de la configuration
  Elasticsearch.

Ces risques sont couverts par la séparation des composants, la validation avant
écriture et les tests unitaires, HTTP et Elasticsearch.

## Hors périmètre

Cette tranche ne couvre pas :

- une interface d’administration ;
- un appel automatique depuis ABESstp ;
- l’authentification des agents ;
- les demandes Google Search Console ou Bing ;
- la vérification de la pièce d’identité ;
- un historique complet des décisions ;
- la suppression ou la modification automatique du `robots.txt` existant.
