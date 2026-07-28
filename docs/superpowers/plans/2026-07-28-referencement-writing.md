# Écriture du référencement — Plan d’implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exposer une API HTTP interne capable d’activer ou de désactiver `noIndex` pour un NNT, un PPN ou un numéro de sujet et d’enregistrer la décision dans l’index Elasticsearch `referencement`.

**Architecture:** Un contrôleur HTTP traduit la requête en commande métier. Un service valide la cohérence entre le type de page et l’identifiant, garantit l’idempotence, produit `updatedAt`, puis délègue la lecture et l’écriture à un gateway documentaire Elasticsearch distinct du gateway de cycle de vie de l’index. La configuration sécurisée du client Elasticsearch est partagée entre le profil ponctuel `init-index` et le profil HTTP normal.

**Tech Stack:** Java 17, Spring Boot 3.0.6, Spring MVC, Jakarta Bean Validation, Elasticsearch Java Client 8.10.3, Jackson, JUnit 5, AssertJ, MockMvc et Testcontainers 1.21.4.

## Global Constraints

- Conserver Java 17, Spring Boot 3.0.6 et Elasticsearch Java Client 8.10.3.
- Utiliser exactement l’index Elasticsearch `referencement`.
- Utiliser l’identifiant canonique comme `_id` Elasticsearch.
- Conserver le document avec `noIndex: false` lors d’une réactivation.
- Ne jamais accepter `updatedAt` depuis la requête HTTP ; le produire en UTC.
- Une requête `PUT` strictement identique ne doit ni réécrire le document ni modifier `updatedAt`.
- Valider les NNT avec `[0-9]{4}[A-Z0-9]{8}`.
- Valider les PPN avec `[0-9]{8}[0-9X]`, sans recalcul de clé.
- Valider les numéros de sujet avec `s[0-9]+`.
- Refuser les champs JSON inconnus et toute incohérence entre identifiant et `pageType`.
- Garder l’API interne ; ne pas ajouter d’interface, d’appel ABESstp ni d’authentification dans SOA-820.
- Garder le gateway documentaire séparé du gateway de création et de contrôle de l’index.
- Ne jamais attribuer `create_index` ou `delete_index` au profil HTTP normal.
- Rédiger les commits en français avec `Jerome Villiseck <jvk@abes.fr>`.
- L’import du `robots.txt` fera l’objet d’un plan séparé après cette tranche.

---

## Structure des fichiers

### Domaine et service

- `ReferencementPageType.java` : énumération et format canonique de chaque type.
- `ReferencementDocument.java` : source stockée dans Elasticsearch.
- `ReferencementWriteCommand.java` : commande indépendante de HTTP.
- `ReferencementWriteResult.java` : identifiant et document retournés au contrôleur.
- `ReferencementDocumentGateway.java` : port de lecture et d’écriture des documents.
- `ReferencementValidationException.java` : erreur métier de validation.
- `ReferencementDocumentAccessException.java` : erreur technique contextualisée.
- `ReferencementWriteService.java` : validation, idempotence, horodatage et écriture.

### Elasticsearch et configuration

- `ElasticsearchReferencementDocumentGateway.java` : adaptateur documentaire.
- `ElasticsearchConfiguration.java` : client sécurisé partagé entre les profils.
- `ReferencementIndexConfiguration.java` : beans réservés à `init-index`.
- `ReferencementWriteConfiguration.java` : beans du profil HTTP normal.

### HTTP

- `ReferencementWriteRequest.java` : corps HTTP validé.
- `ReferencementWriteResponse.java` : représentation de la réponse.
- `ReferencementController.java` : endpoint `PUT`.
- `ReferencementExceptionHandler.java` : réponses `application/problem+json`.

### Tests

- `ReferencementWriteServiceTest.java` : règles métier et idempotence.
- `ReferencementIndexIntegrationTest.java` : persistance réelle Elasticsearch.
- `ReferencementIndexProfileTest.java` : séparation des profils.
- `ReferencementControllerTest.java` : contrat HTTP et erreurs.

---

### Task 1: Écrire un NNT avec un horodatage serveur

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocument.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteCommand.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteResult.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java`

**Interfaces:**
- Produces: `ReferencementWriteService.write(String, ReferencementWriteCommand)`.
- Produces: `ReferencementDocumentGateway.save(String, ReferencementDocument)`.
- Produces: `ReferencementWriteResult(String, ReferencementDocument)`.
- Consumes: une `Clock` injectée pour produire `updatedAt`.

- [ ] **Step 1: Écrire le test rouge d’activation d’un NNT**

Créer `ReferencementWriteServiceTest.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencementWriteServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T09:15:30Z");

    @Test
    void activeNoIndexPourUnNnt() {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReferencementWriteCommand command = new ReferencementWriteCommand(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr"
        );

        ReferencementWriteResult result =
                service.write("2024AIXM0640", command);

        assertThat(result.id()).isEqualTo("2024AIXM0640");
        assertThat(result.document()).isEqualTo(new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                NOW
        ));
        assertThat(gateway.savedId).isEqualTo("2024AIXM0640");
        assertThat(gateway.savedDocument).isEqualTo(result.document());
    }

    @Test
    void reactiveLIndexationEnConservantLeDocument() {
        RecordingGateway gateway = new RecordingGateway();
        ReferencementWriteService service = new ReferencementWriteService(
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service.write(
                "2024AIXM0640",
                new ReferencementWriteCommand(
                        ReferencementPageType.THESE_SOUTENUE,
                        true,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        ReferencementWriteResult result = service.write(
                "2024AIXM0640",
                new ReferencementWriteCommand(
                        ReferencementPageType.THESE_SOUTENUE,
                        false,
                        "ABESSTP-12345",
                        "agent@abes.fr"
                )
        );

        assertThat(result.document().noIndex()).isFalse();
        assertThat(result.document().updatedAt()).isEqualTo(NOW);
        assertThat(gateway.savedId).isEqualTo("2024AIXM0640");
        assertThat(gateway.savedDocument).isEqualTo(result.document());
    }

    private static final class RecordingGateway
            implements ReferencementDocumentGateway {

        private String savedId;
        private ReferencementDocument savedDocument;

        @Override
        public void save(String id, ReferencementDocument document) {
            savedId = id;
            savedDocument = document;
        }
    }
}
```

- [ ] **Step 2: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest#activeNoIndexPourUnNnt" test
```

Expected: compilation en échec, car les types du service d’écriture n’existent
pas encore.

- [ ] **Step 3: Créer le modèle et l’implémentation minimale**

Créer `ReferencementPageType.java` :

```java
package fr.abes.thesesapiindexation.referencement;

public enum ReferencementPageType {
    THESE_SOUTENUE,
    PERSONNE,
    THESE_EN_PREPARATION
}
```

Créer `ReferencementDocument.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import java.time.Instant;

public record ReferencementDocument(
        ReferencementPageType pageType,
        boolean noIndex,
        String demandeRef,
        String updatedBy,
        Instant updatedAt
) {
}
```

Créer `ReferencementWriteCommand.java` :

```java
package fr.abes.thesesapiindexation.referencement;

public record ReferencementWriteCommand(
        ReferencementPageType pageType,
        boolean noIndex,
        String demandeRef,
        String updatedBy
) {
}
```

Créer `ReferencementWriteResult.java` :

```java
package fr.abes.thesesapiindexation.referencement;

public record ReferencementWriteResult(
        String id,
        ReferencementDocument document
) {
}
```

Créer `ReferencementDocumentGateway.java` :

```java
package fr.abes.thesesapiindexation.referencement;

public interface ReferencementDocumentGateway {

    void save(String id, ReferencementDocument document);
}
```

Créer `ReferencementWriteService.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import java.time.Clock;

public class ReferencementWriteService {

    private final ReferencementDocumentGateway gateway;
    private final Clock clock;

    public ReferencementWriteService(
            ReferencementDocumentGateway gateway,
            Clock clock
    ) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public ReferencementWriteResult write(
            String id,
            ReferencementWriteCommand command
    ) {
        ReferencementDocument document = new ReferencementDocument(
                command.pageType(),
                command.noIndex(),
                command.demandeRef(),
                command.updatedBy(),
                clock.instant()
        );
        gateway.save(id, document);
        return new ReferencementWriteResult(id, document);
    }
}
```

- [ ] **Step 4: Vérifier le passage au vert**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest" test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocument.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteCommand.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteResult.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java
git commit -m "feat: écrire une décision de référencement"
```

---

### Task 2: Garantir la réactivation et l’idempotence

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java`

**Interfaces:**
- Consumes: `ReferencementWriteService.write(String, ReferencementWriteCommand)`.
- Produces: `ReferencementDocumentGateway.findById(String)`.
- Guarantees: une commande identique ne déclenche pas `save`.

- [ ] **Step 1: Écrire le test rouge d’une requête identique**

Ajouter au test :

```java
@Test
void conserveUpdatedAtQuandLaRequeteEstIdentique() {
    Instant previousUpdate = Instant.parse("2026-07-27T08:00:00Z");
    RecordingGateway gateway = new RecordingGateway();
    gateway.existingDocument = new ReferencementDocument(
            ReferencementPageType.THESE_SOUTENUE,
            true,
            "ABESSTP-12345",
            "agent@abes.fr",
            previousUpdate
    );
    ReferencementWriteService service = new ReferencementWriteService(
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    ReferencementWriteResult result = service.write(
            "2024AIXM0640",
            new ReferencementWriteCommand(
                    ReferencementPageType.THESE_SOUTENUE,
                    true,
                    "ABESSTP-12345",
                    "agent@abes.fr"
            )
    );

    assertThat(result.document().updatedAt()).isEqualTo(previousUpdate);
    assertThat(gateway.saveCount).isZero();
}
```

Modifier `RecordingGateway` :

```java
private ReferencementDocument existingDocument;
private int saveCount;

@Override
public Optional<ReferencementDocument> findById(String id) {
    return Optional.ofNullable(existingDocument);
}

@Override
public void save(String id, ReferencementDocument document) {
    savedId = id;
    savedDocument = document;
    saveCount++;
}
```

Ajouter l’import :

```java
import java.util.Optional;
```

- [ ] **Step 2: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest#conserveUpdatedAtQuandLaRequeteEstIdentique" test
```

Expected: compilation en échec, car `findById` n’existe pas.

- [ ] **Step 3: Ajouter la lecture et la comparaison métier**

Modifier le gateway :

```java
package fr.abes.thesesapiindexation.referencement;

import java.util.Optional;

public interface ReferencementDocumentGateway {

    Optional<ReferencementDocument> findById(String id);

    void save(String id, ReferencementDocument document);
}
```

Ajouter dans le service avant la construction du nouveau document :

```java
var existingDocument = gateway.findById(id);
if (existingDocument
        .filter(document -> hasSameRequestedState(document, command))
        .isPresent()) {
    return new ReferencementWriteResult(id, existingDocument.orElseThrow());
}
```

Ajouter la méthode :

```java
private boolean hasSameRequestedState(
        ReferencementDocument document,
        ReferencementWriteCommand command
) {
    return document.pageType() == command.pageType()
            && document.noIndex() == command.noIndex()
            && document.demandeRef().equals(command.demandeRef())
            && document.updatedBy().equals(command.updatedBy());
}
```

- [ ] **Step 4: Vérifier le passage au vert**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest" test
```

Expected: les deux tests passent.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java
git commit -m "feat: rendre l’écriture de noIndex idempotente"
```

---

### Task 3: Valider NNT, PPN et numéros de sujet

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementValidationException.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java`

**Interfaces:**
- Produces: `ReferencementPageType.accepts(String)`.
- Produces: `ReferencementValidationException`.
- Guarantees: une entrée invalide ne lit ni n’écrit Elasticsearch.

- [ ] **Step 1: Écrire les tests rouges de formats valides**

Ajouter :

```java
@ParameterizedTest
@MethodSource("validIdentifiers")
void accepteLesIdentifiantsCanoniques(
        ReferencementPageType pageType,
        String id
) {
    RecordingGateway gateway = new RecordingGateway();
    ReferencementWriteService service = new ReferencementWriteService(
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    service.write(
            id,
            new ReferencementWriteCommand(
                    pageType,
                    true,
                    "ABESSTP-12345",
                    "agent@abes.fr"
            )
    );

    assertThat(gateway.savedId).isEqualTo(id);
}

private static Stream<Arguments> validIdentifiers() {
    return Stream.of(
            Arguments.of(
                    ReferencementPageType.THESE_SOUTENUE,
                    "2024AIXM0640"
            ),
            Arguments.of(
                    ReferencementPageType.PERSONNE,
                    "270350292"
            ),
            Arguments.of(
                    ReferencementPageType.PERSONNE,
                    "14424943X"
            ),
            Arguments.of(
                    ReferencementPageType.THESE_EN_PREPARATION,
                    "s233841"
            )
    );
}
```

Ajouter les imports :

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
```

- [ ] **Step 2: Ajouter les tests rouges de rejet**

Ajouter :

```java
@ParameterizedTest
@MethodSource("invalidIdentifiers")
void rejetteUnIdentifiantIncompatible(
        ReferencementPageType pageType,
        String id
) {
    RecordingGateway gateway = new RecordingGateway();
    ReferencementWriteService service = new ReferencementWriteService(
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    assertThatThrownBy(() -> service.write(
            id,
            new ReferencementWriteCommand(
                    pageType,
                    true,
                    "ABESSTP-12345",
                    "agent@abes.fr"
            )
    ))
            .isInstanceOf(ReferencementValidationException.class)
            .hasMessageContaining(id)
            .hasMessageContaining(pageType.name());

    assertThat(gateway.findCount).isZero();
    assertThat(gateway.saveCount).isZero();
}

private static Stream<Arguments> invalidIdentifiers() {
    return Stream.of(
            Arguments.of(
                    ReferencementPageType.THESE_SOUTENUE,
                    "s233841"
            ),
            Arguments.of(
                    ReferencementPageType.THESE_SOUTENUE,
                    "2024aixm0640"
            ),
            Arguments.of(
                    ReferencementPageType.PERSONNE,
                    "27035029"
            ),
            Arguments.of(
                    ReferencementPageType.PERSONNE,
                    "14424943x"
            ),
            Arguments.of(
                    ReferencementPageType.THESE_EN_PREPARATION,
                    "S233841"
            ),
            Arguments.of(
                    ReferencementPageType.THESE_EN_PREPARATION,
                    "s233841 "
            )
    );
}
```

Ajouter l’import :

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

Ajouter le compteur dans `RecordingGateway` :

```java
private int findCount;

@Override
public Optional<ReferencementDocument> findById(String id) {
    findCount++;
    return Optional.ofNullable(existingDocument);
}
```

- [ ] **Step 3: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest" test
```

Expected: compilation en échec, car l’exception de validation n’existe pas.

- [ ] **Step 4: Implémenter les formats canoniques**

Remplacer l’énumération :

```java
package fr.abes.thesesapiindexation.referencement;

import java.util.regex.Pattern;

public enum ReferencementPageType {

    THESE_SOUTENUE("[0-9]{4}[A-Z0-9]{8}"),
    PERSONNE("[0-9]{8}[0-9X]"),
    THESE_EN_PREPARATION("s[0-9]+");

    private final Pattern identifierPattern;

    ReferencementPageType(String identifierPattern) {
        this.identifierPattern = Pattern.compile(identifierPattern);
    }

    public boolean accepts(String identifier) {
        return identifier != null
                && identifierPattern.matcher(identifier).matches();
    }
}
```

Créer l’exception :

```java
package fr.abes.thesesapiindexation.referencement;

public class ReferencementValidationException extends RuntimeException {

    public ReferencementValidationException(
            String identifier,
            ReferencementPageType pageType
    ) {
        super(
                "L’identifiant " + identifier
                        + " est incompatible avec le type " + pageType
        );
    }
}
```

Ajouter au début de `write`, avant `findById` :

```java
if (!command.pageType().accepts(id)) {
    throw new ReferencementValidationException(id, command.pageType());
}
```

- [ ] **Step 5: Vérifier le passage au vert**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest" test
```

Expected: tous les tests du service passent.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementValidationException.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java
git commit -m "feat: valider les identifiants de référencement"
```

---

### Task 4: Persister les documents avec le client Elasticsearch

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentAccessException.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchReferencementDocumentGateway.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexIntegrationTest.java`

**Interfaces:**
- Consumes: `ReferencementDocument` et un `ElasticsearchClient`.
- Produces: `findById`, `save` et `createIfAbsent`.
- Guarantees: `createIfAbsent` ne remplace jamais une décision existante.

- [ ] **Step 1: Écrire le test d’intégration rouge de lecture et d’écriture**

Ajouter dans `ReferencementIndexIntegrationTest` :

```java
@ParameterizedTest
@MethodSource("documentsToPersist")
void ecritEtRelitLesTroisTypesDIdentifiants(
        String id,
        ReferencementDocument document
) {
    initializer.initialize();
    ReferencementDocumentGateway documentGateway =
            new ElasticsearchReferencementDocumentGateway(
                    client,
                    INDEX_NAME
            );

    documentGateway.save(id, document);

    assertThat(documentGateway.findById(id)).contains(document);
}

private static Stream<Arguments> documentsToPersist() {
    Instant updatedAt = Instant.parse("2026-07-28T09:15:30Z");
    return Stream.of(
            Arguments.of(
                    "2024AIXM0640",
                    new ReferencementDocument(
                            ReferencementPageType.THESE_SOUTENUE,
                            true,
                            "ABESSTP-12345",
                            "agent@abes.fr",
                            updatedAt
                    )
            ),
            Arguments.of(
                    "270350292",
                    new ReferencementDocument(
                            ReferencementPageType.PERSONNE,
                            true,
                            "ABESSTP-12345",
                            "agent@abes.fr",
                            updatedAt
                    )
            ),
            Arguments.of(
                    "s233841",
                    new ReferencementDocument(
                            ReferencementPageType.THESE_EN_PREPARATION,
                            true,
                            "ABESSTP-12345",
                            "agent@abes.fr",
                            updatedAt
                    )
            )
    );
}
```

Ajouter les imports :

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;
```

Ajouter également, avant toute implémentation :

```java
@Test
void remplaceLeDocumentLorsDeLaReactivation() {
    initializer.initialize();
    ReferencementDocumentGateway documentGateway =
            new ElasticsearchReferencementDocumentGateway(
                    client,
                    INDEX_NAME
            );
    ReferencementDocument active = new ReferencementDocument(
            ReferencementPageType.THESE_SOUTENUE,
            true,
            "ABESSTP-12345",
            "agent@abes.fr",
            Instant.parse("2026-07-28T09:15:30Z")
    );
    ReferencementDocument inactive = new ReferencementDocument(
            ReferencementPageType.THESE_SOUTENUE,
            false,
            "ABESSTP-12345",
            "agent@abes.fr",
            Instant.parse("2026-07-28T10:00:00Z")
    );

    documentGateway.save("2024AIXM0640", active);
    documentGateway.save("2024AIXM0640", inactive);

    assertThat(documentGateway.findById("2024AIXM0640"))
            .contains(inactive);
}

@Test
void neRemplacePasUnDocumentExistantPendantUnImport() {
    initializer.initialize();
    ReferencementDocumentGateway documentGateway =
            new ElasticsearchReferencementDocumentGateway(
                    client,
                    INDEX_NAME
            );
    ReferencementDocument existing = new ReferencementDocument(
            ReferencementPageType.PERSONNE,
            false,
            "ABESSTP-12345",
            "agent@abes.fr",
            Instant.parse("2026-07-28T09:15:30Z")
    );
    ReferencementDocument imported = new ReferencementDocument(
            ReferencementPageType.PERSONNE,
            true,
            "IMPORT-ROBOTS-INITIAL",
            "robots.txt-importer",
            Instant.parse("2026-07-28T10:00:00Z")
    );
    documentGateway.save("270350292", existing);

    boolean created =
            documentGateway.createIfAbsent("270350292", imported);

    assertThat(created).isFalse();
    assertThat(documentGateway.findById("270350292"))
            .contains(existing);
}
```

- [ ] **Step 2: Vérifier les échecs attendus**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexIntegrationTest" test
```

Expected: compilation en échec, car l’adaptateur documentaire n’existe pas.

- [ ] **Step 3: Créer l’exception technique**

```java
package fr.abes.thesesapiindexation.referencement;

public class ReferencementDocumentAccessException extends RuntimeException {

    public ReferencementDocumentAccessException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Ajouter la création conditionnelle au port**

```java
package fr.abes.thesesapiindexation.referencement;

import java.util.Optional;

public interface ReferencementDocumentGateway {

    Optional<ReferencementDocument> findById(String id);

    void save(String id, ReferencementDocument document);

    boolean createIfAbsent(String id, ReferencementDocument document);
}
```

Mettre à jour `RecordingGateway` dans le test du service :

```java
@Override
public boolean createIfAbsent(
        String id,
        ReferencementDocument document
) {
    if (existingDocument != null) {
        return false;
    }
    save(id, document);
    return true;
}
```

- [ ] **Step 5: Créer l’adaptateur Elasticsearch**

Créer `ElasticsearchReferencementDocumentGateway.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;

import java.io.IOException;
import java.util.Optional;

public class ElasticsearchReferencementDocumentGateway
        implements ReferencementDocumentGateway {

    private static final String VERSION_CONFLICT =
            "version_conflict_engine_exception";

    private final ElasticsearchClient client;
    private final String indexName;

    public ElasticsearchReferencementDocumentGateway(
            ElasticsearchClient client,
            String indexName
    ) {
        this.client = client;
        this.indexName = indexName;
    }

    @Override
    public Optional<ReferencementDocument> findById(String id) {
        try {
            var response = client.get(
                    request -> request.index(indexName).id(id),
                    ReferencementDocument.class
            );
            return response.found()
                    ? Optional.ofNullable(response.source())
                    : Optional.empty();
        } catch (ElasticsearchException | IOException exception) {
            throw accessFailure("lire", id, exception);
        }
    }

    @Override
    public void save(String id, ReferencementDocument document) {
        try {
            client.index(request -> request
                    .index(indexName)
                    .id(id)
                    .document(document));
        } catch (ElasticsearchException | IOException exception) {
            throw accessFailure("écrire", id, exception);
        }
    }

    @Override
    public boolean createIfAbsent(
            String id,
            ReferencementDocument document
    ) {
        try {
            client.index(request -> request
                    .index(indexName)
                    .id(id)
                    .opType(OpType.Create)
                    .document(document));
            return true;
        } catch (ElasticsearchException exception) {
            if (VERSION_CONFLICT.equals(exception.error().type())) {
                return false;
            }
            throw accessFailure("créer", id, exception);
        } catch (IOException exception) {
            throw accessFailure("créer", id, exception);
        }
    }

    private ReferencementDocumentAccessException accessFailure(
            String operation,
            String id,
            Exception cause
    ) {
        return new ReferencementDocumentAccessException(
                "Impossible de " + operation
                        + " le référencement " + id
                        + " dans l’index " + indexName,
                cause
        );
    }
}
```

- [ ] **Step 6: Enregistrer correctement les dates Java**

Modifier le bean `elasticsearchJsonpMapper` dans
`ElasticsearchConfiguration.java` :

```java
@Bean
JacksonJsonpMapper elasticsearchJsonpMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
    objectMapper.disable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
    );
    return new JacksonJsonpMapper(objectMapper);
}
```

Ajouter les imports :

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
```

Dans `ReferencementIndexIntegrationTest.setUp`, remplacer la construction du
mapper par :

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.findAndRegisterModules();
objectMapper.disable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
);
JacksonJsonpMapper mapper = new JacksonJsonpMapper(objectMapper);
```

Ajouter également dans ce test :

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
```

- [ ] **Step 7: Vérifier le passage au vert**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexIntegrationTest" test
```

Expected: les trois types d’identifiants, le remplacement et la création
conditionnelle passent contre Elasticsearch 8.10.3 en HTTPS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentGateway.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementDocumentAccessException.java src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchReferencementDocumentGateway.java src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchConfiguration.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexIntegrationTest.java
git commit -m "feat: persister les décisions dans Elasticsearch"
```

---

### Task 5: Partager le client sécurisé entre les profils

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchConfiguration.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexConfiguration.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteConfiguration.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-init-index.properties`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexProfileTest.java`

**Interfaces:**
- Consumes: client HTTPS/CA/authentification existant.
- Produces: `ReferencementWriteService` et `ReferencementDocumentGateway` hors
  du profil `init-index`.
- Guarantees: l’initialiseur reste absent du profil HTTP normal.

- [ ] **Step 1: Écrire les assertions rouges de profils**

Remplacer le test `demarreSansClientElasticsearchHorsDuProfilInitIndex` par :

```java
@Test
void chargeLeClientEtLeServiceDEcritureHorsDuProfilInitIndex() {
    new ApplicationContextRunner()
            .withUserConfiguration(ThesesApiIndexationApplication.class)
            .withPropertyValues(
                    "es.hostname=localhost",
                    "es.port=9200",
                    "es.protocol=http"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context)
                        .hasSingleBean(ElasticsearchClient.class);
                assertThat(context)
                        .hasSingleBean(ReferencementDocumentGateway.class);
                assertThat(context)
                        .hasSingleBean(ReferencementWriteService.class);
                assertThat(context)
                        .doesNotHaveBean(ReferencementIndexGateway.class);
                assertThat(context)
                        .doesNotHaveBean(ReferencementIndexInitializer.class);
            });
}
```

Ajouter au test du profil `init-index` les assertions :

```java
assertThat(context).hasSingleBean(ReferencementIndexGateway.class);
assertThat(context).doesNotHaveBean(ReferencementWriteService.class);
```

- [ ] **Step 2: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexProfileTest" test
```

Expected: FAIL, car le client et le service ne sont pas chargés dans le profil
normal.

- [ ] **Step 3: Rendre la configuration du client commune**

Dans `ElasticsearchConfiguration.java` :

- supprimer `@Profile("init-index")` ;
- supprimer le bean `referencementIndexGateway` ;
- conserver le client, le mapper JSON et le transport sécurisé.

Le bean supprimé est exactement :

```java
@Bean
ReferencementIndexGateway referencementIndexGateway(
        ElasticsearchClient elasticsearchClient,
        JacksonJsonpMapper elasticsearchJsonpMapper
) {
    return new ElasticsearchReferencementIndexGateway(
            elasticsearchClient,
            elasticsearchJsonpMapper
    );
}
```

- [ ] **Step 4: Réserver le gateway d’index au profil ponctuel**

Ajouter dans `ReferencementIndexConfiguration.java` :

```java
@Bean
@ConditionalOnMissingBean(ReferencementIndexGateway.class)
ReferencementIndexGateway referencementIndexGateway(
        ElasticsearchClient elasticsearchClient,
        JacksonJsonpMapper elasticsearchJsonpMapper
) {
    return new ElasticsearchReferencementIndexGateway(
            elasticsearchClient,
            elasticsearchJsonpMapper
    );
}
```

Ajouter les imports :

```java
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
```

La condition permet au test de profil de conserver son faux gateway sans
charger un second bean.

- [ ] **Step 5: Créer la configuration du service d’écriture**

Créer `ReferencementWriteConfiguration.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("!init-index")
@EnableConfigurationProperties(ReferencementIndexProperties.class)
public class ReferencementWriteConfiguration {

    @Bean
    Clock referencementClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReferencementDocumentGateway referencementDocumentGateway(
            ElasticsearchClient client,
            ReferencementIndexProperties properties
    ) {
        return new ElasticsearchReferencementDocumentGateway(
                client,
                properties.name()
        );
    }

    @Bean
    ReferencementWriteService referencementWriteService(
            ReferencementDocumentGateway gateway,
            Clock referencementClock
    ) {
        return new ReferencementWriteService(
                gateway,
                referencementClock
        );
    }
}
```

- [ ] **Step 6: Déplacer les propriétés de connexion communes**

Ajouter à `application.properties` :

```properties
es.hostname=${ES_HOSTNAME:localhost}
es.port=${ES_PORT:9200}
es.protocol=${ES_PROTOCOL:http}
es.username=${ES_USERNAME:}
es.password=${ES_PASSWORD:}
```

Conserver uniquement dans `application-init-index.properties` :

```properties
spring.main.web-application-type=none
```

`ES_CA_CERTIFICATE` reste lié directement à `es.ca-certificate` par le binding
relâché de Spring Boot.

- [ ] **Step 7: Vérifier le passage au vert et la non-régression**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexProfileTest,ReferencementIndexIntegrationTest" test
```

Expected: tous les tests de profils et d’intégration passent.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ElasticsearchConfiguration.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexConfiguration.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteConfiguration.java src/main/resources/application.properties src/main/resources/application-init-index.properties src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexProfileTest.java
git commit -m "refactor: partager le client Elasticsearch sécurisé"
```

---

### Task 6: Exposer le contrat HTTP interne

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteRequest.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteResponse.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementController.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementExceptionHandler.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementControllerTest.java`

**Interfaces:**
- Consumes: `ReferencementWriteService.write`.
- Produces: `PUT /api/v1/referencements/{identifiant}`.
- Produces: `200` en JSON ; `400` et `503` en
  `application/problem+json`.

- [ ] **Step 1: Ajouter les dépendances Web et Validation**

Ajouter dans `pom.xml` :

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Ajouter à `application.properties` :

```properties
spring.jackson.deserialization.fail-on-unknown-properties=true
```

- [ ] **Step 2: Écrire le test HTTP rouge du cas nominal**

Créer `ReferencementControllerTest.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReferencementController.class)
class ReferencementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReferencementWriteService service;

    @Test
    void activeNoIndexEtRetourneLEtatComplet() throws Exception {
        ReferencementDocument document = new ReferencementDocument(
                ReferencementPageType.THESE_SOUTENUE,
                true,
                "ABESSTP-12345",
                "agent@abes.fr",
                Instant.parse("2026-07-28T09:15:30Z")
        );
        given(service.write(eq("2024AIXM0640"), any()))
                .willReturn(new ReferencementWriteResult(
                        "2024AIXM0640",
                        document
                ));

        mockMvc.perform(put(
                        "/api/v1/referencements/{identifiant}",
                        "2024AIXM0640"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageType": "THESE_SOUTENUE",
                                  "noIndex": true,
                                  "demandeRef": "ABESSTP-12345",
                                  "updatedBy": "agent@abes.fr"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id")
                        .value("2024AIXM0640"))
                .andExpect(jsonPath("$.pageType")
                        .value("THESE_SOUTENUE"))
                .andExpect(jsonPath("$.noIndex").value(true))
                .andExpect(jsonPath("$.demandeRef")
                        .value("ABESSTP-12345"))
                .andExpect(jsonPath("$.updatedBy")
                        .value("agent@abes.fr"))
                .andExpect(jsonPath("$.updatedAt")
                        .value("2026-07-28T09:15:30Z"));
    }
}
```

- [ ] **Step 3: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementControllerTest#activeNoIndexEtRetourneLEtatComplet" test
```

Expected: compilation en échec, car les classes HTTP n’existent pas.

- [ ] **Step 4: Créer les DTO HTTP**

Créer `ReferencementWriteRequest.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReferencementWriteRequest(
        @NotNull ReferencementPageType pageType,
        @NotNull Boolean noIndex,
        @NotBlank @Size(max = 64) String demandeRef,
        @NotBlank @Size(max = 255) String updatedBy
) {

    ReferencementWriteCommand toCommand() {
        return new ReferencementWriteCommand(
                pageType,
                noIndex,
                demandeRef,
                updatedBy
        );
    }
}
```

Créer `ReferencementWriteResponse.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import java.time.Instant;

public record ReferencementWriteResponse(
        String id,
        ReferencementPageType pageType,
        boolean noIndex,
        String demandeRef,
        String updatedBy,
        Instant updatedAt
) {

    static ReferencementWriteResponse from(
            ReferencementWriteResult result
    ) {
        ReferencementDocument document = result.document();
        return new ReferencementWriteResponse(
                result.id(),
                document.pageType(),
                document.noIndex(),
                document.demandeRef(),
                document.updatedBy(),
                document.updatedAt()
        );
    }
}
```

- [ ] **Step 5: Créer le contrôleur**

Créer `ReferencementController.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!init-index")
@RequestMapping("/api/v1/referencements")
public class ReferencementController {

    private final ReferencementWriteService service;

    public ReferencementController(ReferencementWriteService service) {
        this.service = service;
    }

    @PutMapping("/{identifiant}")
    ResponseEntity<ReferencementWriteResponse> write(
            @PathVariable String identifiant,
            @Valid @RequestBody ReferencementWriteRequest request
    ) {
        ReferencementWriteResult result =
                service.write(identifiant, request.toCommand());
        return ResponseEntity.ok(
                ReferencementWriteResponse.from(result)
        );
    }
}
```

- [ ] **Step 6: Vérifier le cas nominal**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementControllerTest#activeNoIndexEtRetourneLEtatComplet" test
```

Expected: PASS.

- [ ] **Step 7: Ajouter les tests rouges de requêtes invalides**

Ajouter dans `ReferencementControllerTest` :

```java
@Test
void rejetteUnChampObligatoireAbsent() throws Exception {
    mockMvc.perform(put(
                    "/api/v1/referencements/{identifiant}",
                    "2024AIXM0640"
            )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "pageType": "THESE_SOUTENUE",
                              "demandeRef": "ABESSTP-12345",
                              "updatedBy": "agent@abes.fr"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
            ))
            .andExpect(jsonPath("$.title")
                    .value("Requête de référencement invalide"));
}

@Test
void rejetteUnChampJsonInconnu() throws Exception {
    mockMvc.perform(put(
                    "/api/v1/referencements/{identifiant}",
                    "2024AIXM0640"
            )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "pageType": "THESE_SOUTENUE",
                              "noIndex": true,
                              "demandeRef": "ABESSTP-12345",
                              "updatedBy": "agent@abes.fr",
                              "motifLibre": "interdit"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
            ));
}

@Test
void rejetteUnIdentifiantIncompatible() throws Exception {
    given(service.write(eq("s233841"), any()))
            .willThrow(new ReferencementValidationException(
                    "s233841",
                    ReferencementPageType.THESE_SOUTENUE
            ));

    mockMvc.perform(put(
                    "/api/v1/referencements/{identifiant}",
                    "s233841"
            )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "pageType": "THESE_SOUTENUE",
                              "noIndex": true,
                              "demandeRef": "ABESSTP-12345",
                              "updatedBy": "agent@abes.fr"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail")
                    .value("L’identifiant s233841 est incompatible avec le type THESE_SOUTENUE"));
}
```

- [ ] **Step 8: Ajouter le test rouge d’indisponibilité Elasticsearch**

Ajouter :

```java
@Test
void retourneServiceIndisponibleQuandElasticsearchEchoue()
        throws Exception {
    given(service.write(eq("2024AIXM0640"), any()))
            .willThrow(new ReferencementDocumentAccessException(
                    "Impossible d’écrire le référencement 2024AIXM0640 dans l’index referencement",
                    new IOException("Connexion refusée")
            ));

    mockMvc.perform(put(
                    "/api/v1/referencements/{identifiant}",
                    "2024AIXM0640"
            )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "pageType": "THESE_SOUTENUE",
                              "noIndex": true,
                              "demandeRef": "ABESSTP-12345",
                              "updatedBy": "agent@abes.fr"
                            }
                            """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
            ))
            .andExpect(jsonPath("$.title")
                    .value("Elasticsearch indisponible"));
}
```

Ajouter l’import :

```java
import java.io.IOException;
```

- [ ] **Step 9: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementControllerTest" test
```

Expected: les tests d’erreurs échouent, car le handler n’existe pas.

- [ ] **Step 10: Créer le handler `ProblemDetail`**

Créer `ReferencementExceptionHandler.java` :

```java
package fr.abes.thesesapiindexation.referencement;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.net.URI;

@RestControllerAdvice
public class ReferencementExceptionHandler {

    @ExceptionHandler(ReferencementValidationException.class)
    ProblemDetail validationFailure(
            ReferencementValidationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail bodyValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String detail = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> "Le champ " + error.getField()
                        + " " + error.getDefaultMessage())
                .orElse("Le corps de la requête est invalide");
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                detail,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Requête de référencement invalide",
                "Le corps JSON est invalide",
                request
        );
    }

    @ExceptionHandler(ReferencementDocumentAccessException.class)
    ProblemDetail elasticsearchFailure(
            ReferencementDocumentAccessException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Elasticsearch indisponible",
                exception.getMessage(),
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail
        );
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
```

- [ ] **Step 11: Vérifier le passage au vert**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementControllerTest" test
```

Expected: tous les tests du contrôleur passent.

- [ ] **Step 12: Commit**

```powershell
git add pom.xml src/main/resources/application.properties src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteRequest.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteResponse.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementController.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementExceptionHandler.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementControllerTest.java
git commit -m "feat: exposer le contrat HTTP de référencement"
```

---

### Task 7: Documenter et valider la tranche complète

**Files:**
- Modify: `README.md`
- Verify: all source and test files

**Interfaces:**
- Consumes: l’endpoint, les variables Elasticsearch et le profil `init-index`.
- Produces: documentation exploitable et build entièrement vert.

- [ ] **Step 1: Documenter l’appel HTTP**

Ajouter au `README.md` :

````markdown
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
````

- [ ] **Step 2: Vérifier les dépendances Elasticsearch**

Run:

```powershell
mvn --batch-mode dependency:tree "-Dincludes=co.elastic.clients:elasticsearch-java,org.elasticsearch.client:elasticsearch-rest-client"
```

Expected: les deux clients sont exactement en version `8.10.3`.

- [ ] **Step 3: Lancer le build complet**

Run:

```powershell
mvn --batch-mode clean verify
```

Expected: `BUILD SUCCESS`, aucun échec et aucun test ignoré.

- [ ] **Step 4: Contrôler le diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: aucune erreur d’espace ; seul `README.md` reste non commité à cette
étape.

- [ ] **Step 5: Commit**

```powershell
git add README.md
git commit -m "docs: documenter l’API de référencement"
```

- [ ] **Step 6: Vérifier la branche**

Run:

```powershell
git status -sb
git log --oneline --decorate -10
```

Expected: arbre de travail propre sur `SOA-820-referencement`, avec les commits
des sept tâches au-dessus du commit de spécification `1203505`.

---

## Critères de fin

- `PUT /api/v1/referencements/{identifiant}` retourne `200 OK`.
- Un NNT, un PPN et un numéro de sujet sont réellement persistés dans
  Elasticsearch.
- `noIndex: false` remplace l’état sans supprimer le document.
- Une requête strictement identique conserve `updatedAt`.
- Les formats invalides, champs manquants et champs inconnus retournent `400`.
- Une erreur Elasticsearch retourne `503` sans exposer de secret.
- Le profil `init-index` continue de créer ou vérifier l’index puis de se
  terminer.
- Le profil normal possède le client et le service d’écriture, mais aucun
  initialiseur d’index.
- `mvn --batch-mode clean verify` réussit intégralement.
- Le dépôt reste prêt pour un plan séparé consacré à l’import ponctuel du
  `robots.txt`.
