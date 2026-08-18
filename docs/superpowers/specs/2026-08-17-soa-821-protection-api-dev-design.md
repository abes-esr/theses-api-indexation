# SOA-821 — Déploiement et protection de l’API d’administration en DEV

## Contexte

SOA-820 a livré le registre Elasticsearch `referencement`, le contrat
d’écriture, l’activation et la désactivation de `noIndex`, l’import ponctuel du
`robots.txt`, l’image Docker et les deux jobs `init-index` et `import-robots`.
L’index DEV contient les règles importées et le compte
`theses-api-indexation` possède déjà des droits limités à cet index.

SOA-821 rend maintenant l’API d’administration disponible en continu dans la
pile `theses-docker`, sans l’exposer directement sur l’hôte. Les écritures
doivent être réservées à des agents authentifiés par Shibboleth et explicitement
autorisés par leur ePPN.

## Objectif

Déployer en DEV un service permanent `theses-api-indexation` qui permet à un
agent autorisé d’activer ou de désactiver `noIndex`, avec une identité
infalsifiable issue de Shibboleth et des droits Elasticsearch minimaux.

## Périmètre

### Inclus

- sécurisation de l’API HTTP par Spring Security ;
- authentification applicative à partir de l’en-tête Shibboleth `eppn` ;
- autorisation par une liste d’ePPN explicite ;
- production de `updatedBy` à partir de l’ePPN authentifié ;
- endpoint de santé interne ;
- service Docker Compose permanent sans port hôte ;
- chemin Shibboleth protégé dans `theses-rp` ;
- rôle Elasticsearch explicite de lecture pour les futurs consommateurs ;
- conservation du rôle d’écriture limité à `referencement` ;
- déploiement et recette sur l’environnement DEV uniquement.

### Hors périmètre

- lecture publique de l’état `noIndex` par `theses-api-recherche` (SOA-822) ;
- génération de la balise HTML `noindex` (SOA-823) ;
- exclusion du sitemap (SOA-824) ;
- en-tête `X-Robots-Tag` des exports (SOA-825) ;
- finalisation de la pile complète (SOA-826) ;
- migration fonctionnelle finale (SOA-827) ;
- suppression des directives individuelles du `robots.txt` (SOA-828) ;
- déploiement en TEST ou PROD ;
- interface graphique d’administration.

## Architecture

```text
Agent ABES
    |
    | HTTPS /api/v1/indexation/{identifiant}
    v
theses-rp
    |  session Shibboleth obligatoire
    |  transmet l’en-tête eppn
    v
theses-api-indexation:8994
    |  eppn présent dans la liste autorisée
    |  compte Elasticsearch read/write limité
    v
index referencement
```

Le service n’a aucun port publié sur l’hôte. Le reverse proxy est le seul point
d’entrée depuis l’extérieur de la pile Docker. L’API ne rejoint pas le réseau
Docker partagé : elle utilise un réseau interne avec le seul reverse proxy et
un second réseau interne avec le seul service Elasticsearch. Aucun service TEST
ou PROD n’est modifié.

## Routage HTTP

La route d’administration externe est :

```http
PUT /api/v1/indexation/{identifiant}
```

`theses-rp` la transmet vers la route interne existante :

```http
PUT http://theses-api-indexation:8994/api/v1/referencements/{identifiant}
```

Le préfixe externe distingue clairement l’administration de la future lecture
publique `GET /api/v1/referencement/{id}` prévue dans SOA-822.

## Authentification et autorisation

L’image `docker-shibboleth-renater-sp` active `ShibUseHeaders On` sur les
chemins protégés et expose l’attribut `eduPersonPrincipalName` sous le nom
`eppn`.

La configuration applicative lit :

```text
THESES_NOINDEX_ALLOWED_EPPNS
```

La valeur est une liste séparée par des virgules. L’application supprime les
espaces, normalise les ePPN en minuscules, ignore les doublons et n’accepte
aucun joker. Une liste absente ou vide refuse toutes les écritures.

Règles de sécurité :

- `/actuator/health` est accessible sans ePPN uniquement dans le réseau
  Docker ; cette route n’est pas publiée par `theses-rp` ;
- `PUT /api/v1/referencements/**` exige un ePPN présent et autorisé ;
- une identité absente produit `401 Unauthorized` ;
- une identité présente mais non autorisée produit `403 Forbidden` ;
- toute autre route est refusée ;
- aucune politique CORS permissive n’est ajoutée ;
- la sécurité est sans session applicative ;
- la protection CSRF est désactivée pour cette API REST stateless, dont
  l’identité est fournie par le reverse proxy.

La configuration de sécurité n’est pas chargée pour les profils ponctuels
`init-index` et `import-robots`.

## Contrat d’écriture sécurisé

Le corps HTTP ne contient plus `updatedBy` :

```json
{
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ASSISTANCE-12345"
}
```

Le contrôleur ajoute l’ePPN authentifié à la commande métier. La réponse et le
document Elasticsearch conservent le modèle existant :

```json
{
  "id": "2099TEST0001",
  "pageType": "THESE_SOUTENUE",
  "noIndex": true,
  "demandeRef": "ASSISTANCE-12345",
  "updatedBy": "agent.authentifie@abes.fr",
  "updatedAt": "2026-08-17T14:30:00Z"
}
```

Un champ `updatedBy` encore envoyé par un client est rejeté comme champ JSON
inconnu. Le service métier et le document Elasticsearch ne changent pas : seul
le contrôleur HTTP obtient désormais l’auteur depuis l’identité authentifiée.

L’importeur ne passe pas par HTTP et conserve
`updatedBy=robots.txt-importer`.

## Configuration Spring Security

Une propriété typée contient la liste normalisée des ePPN. Un filtre placé
avant le filtre d’authentification anonyme :

1. lit l’unique valeur de l’en-tête `eppn` ;
2. refuse une valeur vide ou multiple ;
3. normalise l’identité en minuscules ;
4. crée une authentification avec le rôle `NOINDEX_ADMIN` si l’ePPN est
   autorisé ;
5. laisse Spring Security produire `401` si l’en-tête est absent ;
6. produit `403` si l’ePPN est connu mais absent de la liste.

Les erreurs de sécurité ne divulguent ni la liste d’autorisation, ni les
secrets Elasticsearch, ni le contenu des certificats.

## Santé du service

`spring-boot-starter-actuator` expose uniquement :

```http
GET /actuator/health
```

Les détails internes ne sont pas rendus publics. L’image installe `curl` pour
le healthcheck Docker. Le healthcheck vérifie le code HTTP de cette route sur
`127.0.0.1:8994`.

La santé HTTP prouve que le processus Spring accepte les requêtes. La recette
DEV vérifie séparément l’accès réel à Elasticsearch et le mapping de
`referencement`.

## Service Docker Compose

Le nouveau service permanent utilise :

- `container_name: theses-api-indexation` ;
- `image: abesesr/theses:${THESES_API_INDEXATION_VERSION}` ;
- `restart: unless-stopped` ;
- `SERVER_PORT: 8994` ;
- le compte `THESES_API_INDEXATION_ELASTIC_USERNAME` ;
- le certificat `file:/app/certs/ca/ca.crt` monté en lecture seule ;
- `THESES_REFERENCEMENT_INDEX` ;
- `THESES_NOINDEX_ALLOWED_EPPNS` ;
- les limites CPU et mémoire communes ;
- les variables et labels OpenTelemetry habituels ;
- une dépendance vers Elasticsearch et `theses-elasticsearch-setupusers`
  déclarés sains.

Le service déclare le port interne `8994` avec `expose`, sans bloc `ports`.
Les jobs ponctuels existants restent derrière le profil
`referencement-jobs`.

## Rôles Elasticsearch

### Écriture

Le rôle existant `theses-noindex-writer` reste attribué à
`theses-api-indexation` :

```json
{
  "cluster": [],
  "indices": [
    {
      "names": ["referencement"],
      "privileges": ["read", "write", "view_index_metadata"]
    }
  ]
}
```

Il ne permet ni `create_index`, ni `delete_index`, ni l’accès à un autre index.

### Lecture

Un rôle `theses-noindex-reader` est déclaré :

```json
{
  "cluster": [],
  "indices": [
    {
      "names": ["referencement"],
      "privileges": ["read", "view_index_metadata"]
    }
  ]
}
```

Il est ajouté au compte de lecture déjà utilisé par `theses-api-recherche` et
`theses-seo`, en complément de ses droits de lecture actuels. Aucun droit
d’écriture n’est accordé à ces services.

Le compte `elastic` reste réservé au job ponctuel `init-index`.

## Déploiement DEV

L’ordre de déploiement est :

1. publier la nouvelle image `develop-api-indexation` ;
2. mettre à jour `theses-docker` et son `.env` DEV ;
3. recréer `theses-elasticsearch-setupusers` ;
4. vérifier les privilèges de lecture et d’écriture ;
5. exécuter `init-index` et exiger un code `0` ;
6. démarrer `theses-api-indexation` ;
7. attendre son état `healthy` ;
8. recréer `theses-rp` avec le chemin protégé ;
9. exécuter la recette d’authentification et d’écriture ;
10. vérifier qu’aucun port de l’API n’est accessible depuis l’hôte.

Le déploiement s’arrête au premier code non nul. Il ne supprime ni ne recrée
l’index existant.

## Stratégie de tests

### Tests applicatifs

- liste vide : aucune identité autorisée ;
- normalisation des espaces, de la casse et des doublons ;
- ePPN absent : `401` ;
- ePPN non autorisé : `403` ;
- ePPN autorisé : `200` ;
- `updatedBy` provient de l’ePPN ;
- `updatedBy` fourni dans le JSON : `400` ;
- healthcheck accessible sans ePPN ;
- route inconnue refusée ;
- profils ponctuels toujours exécutables ;
- tests existants NNT, PPN, sujet, idempotence et import toujours verts.

### Tests Compose

- configuration valide avec et sans `referencement-jobs` ;
- service permanent présent sans activation de profil ;
- aucun port hôte sur `theses-api-indexation` ;
- route Shibboleth déclarée comme protégée, jamais comme publique ;
- certificat monté en lecture seule ;
- healthcheck, limites et redémarrage présents ;
- secrets absents du dépôt.

### Recette DEV

- accès non authentifié redirigé vers Shibboleth ;
- ePPN authentifié non autorisé refusé ;
- ePPN autorisé capable d’activer puis de désactiver `noIndex` ;
- auteur Elasticsearch identique à l’ePPN ;
- réexécution identique conservant `updatedAt` ;
- compte writer incapable de créer ou supprimer un index ;
- compte reader incapable d’écrire ;
- accès direct au port `8994` depuis l’hôte impossible ;
- `init-index` et `import-robots` non régressifs.

## Risques et parades

- **Contournement du reverse proxy** : aucun port hôte n’est publié.
- **Usurpation de l’auteur** : `updatedBy` provient exclusivement de l’ePPN.
- **Utilisateur fédéré non habilité** : liste fermée d’ePPN, refus par défaut.
- **Droits Elasticsearch excessifs** : rôles limités au seul index.
- **Régression des jobs** : profils ponctuels exclus de la sécurité HTTP et
  rejoués en recette.
- **Divergence de mapping** : `init-index` reste l’unique mécanisme de création
  et de validation du mapping versionné.
- **Usurpation de l’en-tête `eppn` par un autre conteneur** : l’API est isolée
  du réseau Docker partagé ; seuls le reverse proxy et Elasticsearch partagent
  chacun un réseau interne distinct avec elle.

## Critères d’acceptation

SOA-821 est validé en DEV lorsque :

1. le service permanent est `healthy` ;
2. son port n’est pas publié sur l’hôte ;
3. l’API est absente du réseau Docker partagé et la route d’administration
   passe uniquement par le réseau interne du proxy Shibboleth ;
4. seuls les ePPN explicitement autorisés obtiennent `200` ;
5. `updatedBy` est produit par le serveur depuis l’ePPN ;
6. les rôles Elasticsearch respectent les privilèges attendus ;
7. l’activation, l’idempotence et la désactivation fonctionnent ;
8. tous les tests Maven et Compose sont verts ;
9. les jobs de SOA-820 restent opérationnels ;
10. aucun environnement TEST ou PROD n’a été modifié.
