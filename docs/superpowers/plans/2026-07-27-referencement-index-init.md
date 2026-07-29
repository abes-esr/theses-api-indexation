# Initialisation de l’index `referencement` — Plan d’implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fournir un exécutable Spring Boot capable de créer ou vérifier automatiquement l’index Elasticsearch `referencement` sous le profil ponctuel `init-index`.

**Architecture:** Le mapping JSON est embarqué dans l’image de `theses-api-indexation`. Un composant chargé uniquement sous le profil `init-index` vérifie l’existence et la compatibilité de l’index, le crée lorsqu’il est absent, puis termine. Le profil normal ne charge pas ce composant.

**Tech Stack:** Java 17, Maven 3.9, Spring Boot 3.0.6, client Java Elasticsearch 8.10.3, JUnit 5, Testcontainers avec Elasticsearch 8.10.3.

## Contraintes globales

- L’index se nomme exactement `referencement`.
- Le mapping est `src/main/resources/indexs/referencement.json`.
- L’identifiant canonique est porté par `_id`, pas dupliqué dans `_source`.
- Le mapping utilise `dynamic: strict`.
- Aucun code applicatif ne supprime ni ne recrée un index existant.
- Le profil normal ne tente aucune initialisation.
- Les tests unitaires précèdent chaque comportement de production.
- Les titres et descriptions de commits sont rédigés en français.

---

### Tâche 1 : Socle Maven minimal

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/fr/abes/thesesapiindexation/ThesesApiIndexationApplication.java`
- Create: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: aucune.
- Produces: application Spring Boot Java 17, package racine `fr.abes.thesesapiindexation`.

- [ ] **Étape 1 : Déclarer le projet Maven**

Créer un `pom.xml` avec :

```xml
<groupId>fr.abes</groupId>
<artifactId>theses-api-indexation</artifactId>
<version>0.1.0-SNAPSHOT</version>
<properties>
    <java.version>17</java.version>
</properties>
```

Utiliser `spring-boot-starter-parent:3.0.6`, puis ajouter uniquement
`spring-boot-starter`, `spring-boot-starter-test` en portée `test` et le plugin
`spring-boot-maven-plugin`.

- [ ] **Étape 2 : Ajouter le point d’entrée sans comportement métier**

Créer :

```java
package fr.abes.thesesapiindexation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ThesesApiIndexationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThesesApiIndexationApplication.class, args);
    }
}
```

`application.properties` contient seulement :

```properties
spring.application.name=theses-api-indexation
```

- [ ] **Étape 3 : Vérifier le socle**

Run:

```bash
mvn --batch-mode clean package
```

Expected: `BUILD SUCCESS`, aucun test exécuté et
`target/theses-api-indexation-0.1.0-SNAPSHOT.jar` créé.

- [ ] **Étape 4 : Commit**

```bash
git add pom.xml .gitignore src
git commit -m "build: créer le socle Maven de l'API d'indexation"
```

### Tâche 2 : Mapping versionné

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/indexs/referencement.json`
- Create: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementMappingTest.java`

**Interfaces:**
- Consumes: ressources du classpath Spring.
- Produces: mapping JSON strict avec `pageType`, `noIndex`, `demandeRef`, `updatedBy` et `updatedAt`.

- [ ] **Étape 1 : Écrire le test en échec**

Ajouter `com.fasterxml.jackson.core:jackson-databind` au `pom.xml`.

Le test charge `/indexs/referencement.json`, puis vérifie avec Jackson :

```java
assertThat(mapping.at("/mappings/dynamic").asText()).isEqualTo("strict");
assertThat(mapping.at("/mappings/properties/pageType/type").asText()).isEqualTo("keyword");
assertThat(mapping.at("/mappings/properties/noIndex/type").asText()).isEqualTo("boolean");
assertThat(mapping.at("/mappings/properties/demandeRef/type").asText()).isEqualTo("keyword");
assertThat(mapping.at("/mappings/properties/updatedBy/type").asText()).isEqualTo("keyword");
assertThat(mapping.at("/mappings/properties/updatedAt/type").asText()).isEqualTo("date");
```

- [ ] **Étape 2 : Vérifier l’échec RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementMappingTest test
```

Expected: FAIL car la ressource `referencement.json` est absente.

- [ ] **Étape 3 : Ajouter le mapping minimal**

Créer le JSON exact défini dans
`docs/superpowers/specs/2026-07-27-referencement-index-design.md`.

- [ ] **Étape 4 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementMappingTest test
```

Expected: PASS.

- [ ] **Étape 5 : Commit**

```bash
git add src/main/resources/indexs/referencement.json src/test
git commit -m "feat: versionner le mapping de l'index de référencement"
```

### Tâche 3 : Création lorsque l’index est absent

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexProperties.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexGateway.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializer.java`
- Create: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializerTest.java`

**Interfaces:**
- Consumes: `ReferencementIndexGateway.exists(String)` et
  `ReferencementIndexGateway.create(String, InputStream)`.
- Produces: `ReferencementIndexInitializer.initialize()` et propriété
  `referencement.index.name=referencement`.

- [ ] **Étape 1 : Écrire le test en échec**

Créer un faux gateway en mémoire qui retourne `false` à `exists`. Appeler
`initialize()` puis vérifier son état réel :

```java
assertThat(gateway.createdIndex()).isEqualTo("referencement");
assertThat(gateway.createdMapping())
    .contains("\"dynamic\": \"strict\"")
    .contains("\"noIndex\"");
```

- [ ] **Étape 2 : Vérifier RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: erreur de compilation car l’initialiseur n’existe pas.

- [ ] **Étape 3 : Implémenter le minimum**

Ajouter le client `co.elastic.clients:elasticsearch-java:8.10.3`. Définir le
gateway, les propriétés et l’initialiseur qui charge le mapping du classpath
et appelle `create` uniquement lorsque `exists` vaut `false`.

- [ ] **Étape 4 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: PASS.

- [ ] **Étape 5 : Commit**

```bash
git add pom.xml src
git commit -m "feat: créer l'index de référencement lorsqu'il est absent"
```

### Tâche 4 : Contrôle d’un index existant

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexGateway.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializer.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializerTest.java`

**Interfaces:**
- Consumes: `ReferencementIndexGateway.mapping(String)`.
- Produces: validation des types des cinq champs et du mode `dynamic`.

- [ ] **Étape 1 : Tester l’index compatible sans création**

Configurer le faux gateway avec le mapping attendu et vérifier que
`createdIndex()` reste vide après `initialize()`.

- [ ] **Étape 2 : Vérifier RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: FAIL car le mapping existant n’est pas lu.

- [ ] **Étape 3 : Implémenter la validation**

Comparer explicitement `dynamic` et les types des cinq propriétés. Ignorer les
métadonnées supplémentaires renvoyées par Elasticsearch.

- [ ] **Étape 4 : Ajouter les tests incompatibles**

Ajouter un test par rupture :

- `noIndex` de type `keyword` ;
- `updatedAt` absent ;
- `dynamic` différent de `strict`.

Chaque test attend `IllegalStateException` et le nom du champ concerné.

- [ ] **Étape 5 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: PASS pour tous les cas.

- [ ] **Étape 6 : Commit**

```bash
git add src
git commit -m "feat: contrôler le mapping de l'index de référencement"
```

### Tâche 5 : Course de création

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexGateway.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializer.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexInitializerTest.java`

**Interfaces:**
- Consumes: exception métier `ReferencementIndexAlreadyExistsException`.
- Produces: relecture du mapping après une création concurrente.

- [ ] **Étape 1 : Écrire le test concurrent en échec**

Le faux gateway retourne `false`, puis lève
`ReferencementIndexAlreadyExistsException` à la création et fournit ensuite un
mapping compatible. `initialize()` doit réussir.

- [ ] **Étape 2 : Vérifier RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: FAIL avec l’exception de concurrence.

- [ ] **Étape 3 : Implémenter la relecture**

Intercepter uniquement l’exception de concurrence, relire le mapping puis
appliquer la validation de la tâche 4.

- [ ] **Étape 4 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexInitializerTest test
```

Expected: PASS.

- [ ] **Étape 5 : Commit**

```bash
git add src
git commit -m "fix: tolérer la création concurrente de l'index"
```

### Tâche 6 : Activation exclusive du profil `init-index`

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexConfiguration.java`
- Create: `src/main/resources/application-init-index.properties`
- Create: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexProfileTest.java`

**Interfaces:**
- Consumes: profil Spring `init-index`.
- Produces: bean `ReferencementIndexInitializer` uniquement sous ce profil.

- [ ] **Étape 1 : Tester le profil normal en échec**

Utiliser `ApplicationContextRunner` sans profil et vérifier l’absence du bean
`ReferencementIndexInitializer`.

- [ ] **Étape 2 : Tester le profil `init-index` en échec**

Activer le profil, fournir un faux gateway et vérifier la présence du bean.

- [ ] **Étape 3 : Vérifier RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexProfileTest test
```

Expected: FAIL car la configuration conditionnelle est absente.

- [ ] **Étape 4 : Implémenter la configuration**

Déclarer la configuration avec `@Profile("init-index")` et lancer
`initialize()` par un `ApplicationRunner`. Configurer le profil en application
non web :

```properties
spring.main.web-application-type=none
```

- [ ] **Étape 5 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexProfileTest test
```

Expected: PASS.

- [ ] **Étape 6 : Commit**

```bash
git add src
git commit -m "feat: isoler l'initialisation dans le profil init-index"
```

### Tâche 7 : Adaptateur Elasticsearch et test d’intégration

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/fr/abes/thesesapiindexation/config/ElasticsearchConfiguration.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchReferencementIndexGateway.java`
- Create: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexIntegrationTest.java`

**Interfaces:**
- Consumes: `ELASTICSEARCH_URL`, `ELASTICSEARCH_USERNAME`,
  `ELASTICSEARCH_PASSWORD` et `ReferencementIndexGateway`.
- Produces: adaptateur fondé sur `ElasticsearchClient`.

- [ ] **Étape 1 : Écrire le test Testcontainers en échec**

Ajouter le BOM Testcontainers `1.20.6`, puis
`org.testcontainers:elasticsearch` et `org.testcontainers:junit-jupiter` en
portée `test`.

Démarrer `docker.elastic.co/elasticsearch/elasticsearch:8.10.3`, exécuter
l’initialiseur réel, puis lire le mapping réel :

```java
assertThat(client.indices().exists(e -> e.index("referencement")).value())
    .isTrue();
assertThat(mapping.properties().get("noIndex")._kind())
    .isEqualTo(Property.Kind.Boolean);
```

- [ ] **Étape 2 : Vérifier RED**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexIntegrationTest test
```

Expected: erreur de compilation car l’adaptateur n’existe pas.

- [ ] **Étape 3 : Implémenter l’adaptateur**

Implémenter `exists`, `create` et `mapping`. Traduire uniquement
`resource_already_exists_exception` en
`ReferencementIndexAlreadyExistsException`.

- [ ] **Étape 4 : Vérifier GREEN**

Run:

```bash
mvn --batch-mode -Dtest=ReferencementIndexIntegrationTest test
```

Expected: PASS avec Elasticsearch 8.10.3.

- [ ] **Étape 5 : Vérifier toute la tranche**

Run:

```bash
mvn --batch-mode clean verify
```

Expected: `BUILD SUCCESS`, tests unitaires et intégration réussis.

- [ ] **Étape 6 : Commit**

```bash
git add pom.xml src
git commit -m "feat: connecter l'initialisation à Elasticsearch"
```
