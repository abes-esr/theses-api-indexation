# SOA-820 — Import initial du `robots.txt`

Date : 28 juillet 2026
Statut : design validé

## Objectif

Migrer dans l’index Elasticsearch `referencement` les décisions de
désindexation encore présentes dans le fichier public
`https://theses.fr/robots.txt`.

L’import est une opération ponctuelle et explicitement déclenchée. Il ne
constitue ni une synchronisation récurrente du fichier, ni une nouvelle API.

Pour chaque NNT, PPN ou numéro de sujet reconnu, l’import crée un document de
référencement avec `noIndex: true`. Un document déjà présent dans Elasticsearch
n’est jamais modifié.

## Périmètre

Cette tranche comprend :

- le téléchargement du `robots.txt` public ;
- l’analyse stricte de ses directives `Disallow` ;
- la reconnaissance des NNT, PPN et numéros de sujet ;
- la déduplication des identifiants ;
- la création conditionnelle des documents de référencement ;
- un profil Spring ponctuel `import-robots` ;
- un bilan d’exécution et un code de sortie exploitable ;
- les tests unitaires, de profil et d’intégration nécessaires.

Cette tranche ne comprend pas :

- une surveillance périodique du `robots.txt` ;
- la modification du `robots.txt` ;
- la suppression ou la mise à jour de documents déjà présents ;
- l’initialisation de l’index Elasticsearch ;
- une interface HTTP dédiée à l’import.

L’index doit donc avoir été créé au préalable avec le profil `init-index`.

## Décisions fonctionnelles

### Source autoritative

La source par défaut est le fichier réellement publié :

```text
https://theses.fr/robots.txt
```

L’URL reste configurable pour les environnements et les tests. Elle n’est pas
remplacée par une copie embarquée dans l’application ou par une URL liée à une
branche Git.

Le contenu est téléchargé intégralement avant la première écriture
Elasticsearch. Une indisponibilité, un statut HTTP non valide ou une erreur de
lecture fait échouer le job sans aucune écriture.

### Directives reconnues

Une ligne est importable uniquement si elle contient une directive `Disallow`
dont le chemin est exactement constitué d’un identifiant reconnu à la racine :

```text
Disallow: /2024AIXM0640
Disallow: /270350292
Disallow: /s233841
```

Les espaces autour de la directive, du séparateur et du chemin sont tolérés.
Les formats d’identifiants restent ceux du contrat de référencement :

- NNT : `THESE_SOUTENUE` ;
- PPN : `PERSONNE` ;
- numéro préfixé par `s` : `THESE_EN_PREPARATION`.

Les chemins enrichis ou imbriqués ne sont pas importés :

```text
Disallow: /2024AIXM0640.bib
Disallow: /recherche/2024AIXM0640
```

Les règles sans rapport avec une décision de référencement sont ignorées, par
exemple :

```text
User-agent: *
Crawl-delay: 5
Sitemap: https://theses.fr/sitemap.xml
Disallow: /api
Allow: /2024AIXM0640
```

Les commentaires et les lignes vides sont également ignorés.

Une directive correspondant à l’une des trois familles attendues mais
contenant un identifiant mal formé est comptabilisée comme invalide. Les
chemins avec suffixe ou sous-chemin sont comptabilisés comme ignorés.

### Déduplication

Les identifiants valides sont dédupliqués avant toute écriture. La première
occurrence détermine l’ordre de traitement. Chaque identifiant ne produit
qu’une tentative de création, même si le fichier contient plusieurs directives
identiques.

## Document créé

Chaque document créé respecte le contrat suivant :

```json
{
  "pageType": "type déduit de l’identifiant",
  "noIndex": true,
  "demandeRef": "IMPORT-ROBOTS-INITIAL",
  "updatedBy": "robots.txt-importer",
  "updatedAt": "date et heure UTC de l’écriture"
}
```

L’identifiant est utilisé comme `_id` Elasticsearch, comme pour l’API
d’écriture.

## Idempotence et concurrence

L’import utilise exclusivement l’opération Elasticsearch `create` exposée par
`ReferencementDocumentGateway.createIfAbsent`.

- Si le document est absent, il est créé.
- S’il existe déjà, il reste strictement inchangé.
- Un conflit de création concurrente est traité comme un document existant.
- Toute autre erreur Elasticsearch arrête immédiatement le job.

La préservation s’applique notamment à un document existant avec
`noIndex: false`. Une ancienne ligne du `robots.txt` ne peut donc pas réactiver
une désindexation annulée depuis l’API.

Il n’existe pas de rollback global. Si une erreur survient après plusieurs
créations, ces créations sont conservées. Une relance reprend sans risque :
les documents déjà créés sont détectés par `createIfAbsent`, puis les documents
restants sont traités.

## Architecture retenue

### Flux

```text
https://theses.fr/robots.txt
          |
          v
   client HTTP
          |
          v
   parseur strict
          |
          v
 orchestrateur d’import
          |
          v
 service de référencement
          |
          v
 ReferencementDocumentGateway.createIfAbsent
          |
          v
 Elasticsearch
```

### Composants

Le découpage cible est le suivant :

- un client source télécharge le contenu complet depuis l’URL configurée ;
- un parseur pur transforme le contenu en identifiants valides, uniques et
  typés, avec ses compteurs ;
- un orchestrateur parcourt le résultat et collecte le bilan d’écriture ;
- le service de référencement expose une opération de création conditionnelle
  réutilisant la validation et la construction du document ;
- le gateway Elasticsearch existant réalise l’écriture atomique
  `createIfAbsent` ;
- un `ApplicationRunner` activé par `import-robots` déclenche l’ensemble.

Le client HTTP s’appuie sur les bibliothèques déjà disponibles dans le JDK ou
Spring. Aucun appel à l’API HTTP interne de référencement n’est effectué.

### Alternatives écartées

#### Appeler l’API HTTP d’écriture

Cette approche ajouterait un détour réseau et utiliserait un contrat conçu pour
modifier l’état courant. Elle ne garantit pas la préservation atomique des
documents existants.

#### Écrire directement depuis l’importeur

Cette approche utiliserait correctement `createIfAbsent`, mais dupliquerait la
validation des identifiants et la construction des documents. Ces règles
restent centralisées dans le service de référencement.

## Profil `import-robots`

Le job est activé par :

```text
--spring.profiles.active=import-robots
```

Dans ce profil :

- aucun serveur HTTP n’est démarré ;
- le contrôleur de l’API d’écriture n’est pas exposé ;
- le client, le parseur, l’orchestrateur et le runner d’import sont actifs ;
- les composants d’accès à l’index `referencement` restent disponibles ;
- le processus s’arrête automatiquement après l’exécution.

Le profil `import-robots` ne doit pas être combiné avec `init-index`.

Après un import réussi, l’application se termine avec le code `0`. Une erreur
de téléchargement, d’analyse technique ou d’écriture est propagée afin de
produire un code de sortie non nul.

Le profil normal de l’API ne doit jamais s’arrêter automatiquement. Le profil
`init-index` conserve son fonctionnement existant.

## Configuration

La configuration introduit une propriété dédiée :

```properties
referencement.robots.url=https://theses.fr/robots.txt
```

Les délais de connexion et de lecture doivent être bornés et configurables afin
qu’une source distante indisponible ne bloque pas indéfiniment le job.

La connexion Elasticsearch réutilise la configuration sécurisée existante :
HTTPS, certificat d’autorité et authentification. Le compte d’exécution a
uniquement besoin des droits permettant de créer des documents dans l’index
déjà initialisé ; aucun droit d’administration d’index n’est ajouté.

## Bilan d’exécution

Un import terminé journalise un bilan structuré avec :

- le nombre total de lignes lues ;
- le nombre d’identifiants valides et uniques proposés à l’import ;
- le nombre de doublons ;
- le nombre de lignes ignorées ;
- le nombre de directives invalides ;
- le nombre de documents créés ;
- le nombre de documents déjà existants.

Chaque première occurrence valide alimente `valides`. Chaque occurrence
valide supplémentaire du même identifiant alimente `doublons`. Pour un import
réussi, `créés + existants` est donc égal à `valides`.

Exemple :

```text
Import robots.txt terminé :
lignes=201, valides=176, doublons=8, ignorées=12,
invalides=5, créés=160, existants=16
```

En cas d’échec Elasticsearch, le journal identifie l’identifiant concerné et
présente les compteurs atteints avant l’arrêt. Les secrets, mots de passe et
éléments de certificat ne sont jamais journalisés.

## Gestion des erreurs

| Situation | Comportement |
|---|---|
| Source inaccessible | Échec avant toute écriture |
| Statut HTTP non valide | Échec avant toute écriture |
| Contenu impossible à lire | Échec avant toute écriture |
| Ligne hors périmètre | Ligne ignorée et comptabilisée |
| Identifiant candidat invalide | Ligne invalide, sans écriture |
| Identifiant en double | Une seule tentative d’écriture |
| Document déjà présent | Document préservé et comptabilisé |
| Conflit concurrent | Traité comme un document déjà présent |
| Autre erreur Elasticsearch | Arrêt immédiat avec code non nul |

## Stratégie de tests

L’implémentation suit une démarche pilotée par les tests.

### Parseur

Les tests couvrent :

- un NNT valide ;
- un PPN valide ;
- un numéro de sujet valide ;
- les espaces autorisés et les différentes fins de ligne ;
- les lignes vides, commentaires et métadonnées ;
- les suffixes comme `.bib` ;
- les sous-chemins ;
- les identifiants candidats invalides ;
- les doublons ;
- la production exacte des compteurs.

### Service et orchestrateur

Les tests couvrent :

- la déduction du `pageType` ;
- la construction exacte du document ;
- la date UTC contrôlée par une horloge de test ;
- la création d’un document absent ;
- la préservation d’un document existant, notamment `noIndex: false` ;
- les compteurs `créés` et `existants` ;
- l’arrêt au premier échec Elasticsearch ;
- la relance idempotente après un échec partiel.

### Client HTTP et profil

Les tests couvrent :

- une réponse HTTP réussie ;
- un statut HTTP non valide ;
- une erreur de lecture ou un délai dépassé ;
- l’activation du runner uniquement avec `import-robots` ;
- l’absence du contrôleur et du serveur HTTP dans ce profil ;
- l’arrêt automatique après succès ;
- l’absence d’arrêt automatique du profil normal.

### Intégration Elasticsearch

Un test d’intégration vérifie l’import d’au moins :

- un NNT ;
- un PPN ;
- un numéro de sujet.

Il vérifie également qu’un document préexistant n’est pas écrasé.

La validation finale exécute l’intégralité de la suite Maven afin de contrôler
l’absence de régression sur l’initialisation de l’index et l’API d’écriture.

## Risques de régression et protections

### Activation des profils

La modification des expressions de profil pourrait désactiver par erreur le
contrôleur normal ou démarrer un serveur pendant l’import. Des tests de contexte
vérifient explicitement les trois modes : normal, `init-index` et
`import-robots`.

### Arrêt du processus

L’extension de la logique d’arrêt automatique pourrait arrêter l’API normale.
Un test garantit que seuls les profils ponctuels provoquent cet arrêt.

### Écrasement d’une décision récente

Une lecture suivie d’une écriture classique introduirait une course et pourrait
écraser un `noIndex: false`. L’utilisation atomique de `createIfAbsent` est
obligatoire et testée.

### Évolution du fichier public

Le `robots.txt` peut contenir de nouvelles directives sans rapport avec les
identifiants. Le filtrage strict les ignore et le bilan rend leur présence
visible sans créer de faux documents.

### Import partiel

Une panne Elasticsearch peut produire un import partiel. Ce comportement est
accepté : le job échoue de façon visible et sa relance idempotente complète
l’import.

## Critères d’acceptation

La tranche est terminée lorsque :

- le profil `import-robots` télécharge par défaut
  `https://theses.fr/robots.txt` ;
- aucune écriture n’a lieu si le téléchargement échoue ;
- seuls les NNT, PPN et numéros de sujet racine valides sont importés ;
- les doublons ne produisent qu’une tentative ;
- les documents créés respectent exactement le contrat défini ;
- aucun document existant n’est modifié ;
- le bilan complet est journalisé ;
- le job retourne un code cohérent et s’arrête automatiquement ;
- les tests d’import et l’intégralité de la suite Maven réussissent ;
- les profils `init-index` et normal ne subissent aucune régression.
