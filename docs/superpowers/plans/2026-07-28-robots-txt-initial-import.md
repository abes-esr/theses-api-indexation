# Import initial du robots.txt — Plan d’implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Importer ponctuellement dans l’index Elasticsearch `referencement` les NNT, PPN et numéros de sujet valides publiés dans `https://theses.fr/robots.txt`, sans jamais modifier un document existant.

**Architecture:** Un profil Spring `import-robots` télécharge intégralement la source HTTP, la transmet à un parseur strict et dédupliquant, puis confie chaque identifiant valide au service de référencement. Le service construit le document métier et utilise l’écriture atomique `createIfAbsent`; un runner journalise le bilan puis le processus s’arrête.

**Tech Stack:** Java 17, Spring Boot 3.0.6, Java HTTP Client, Elasticsearch Java Client 8.10.3, JUnit 5, AssertJ, Spring Boot Test et Testcontainers 1.21.4.

## Global Constraints

- Conserver Java 17, Spring Boot 3.0.6 et Elasticsearch Java Client 8.10.3.
- Utiliser exactement l’index Elasticsearch `referencement`.
- Utiliser par défaut `https://theses.fr/robots.txt`, avec une URL configurable.
- Télécharger et analyser tout le contenu avant la première écriture.
- Accepter uniquement `Disallow: /{identifiant}` avec un identifiant racine canonique.
- Ignorer les suffixes comme `.bib`, les sous-chemins, les métadonnées, les commentaires et les lignes vides.
- Valider les NNT avec `[0-9]{4}[A-Z0-9]{8}`.
- Valider les PPN avec `[0-9]{8}[0-9X]`, sans recalcul de clé.
- Valider les numéros de sujet avec `s[0-9]+`.
- Dédupliquer les identifiants avant l’écriture, dans l’ordre de leur première occurrence.
- Créer les documents avec `noIndex=true`, `demandeRef=IMPORT-ROBOTS-INITIAL` et `updatedBy=robots.txt-importer`.
- Ne jamais modifier un document déjà présent, notamment avec `noIndex=false`.
- Arrêter l’import au premier échec Elasticsearch, sans rollback global.
- Rendre chaque relance idempotente grâce à `createIfAbsent`.
- Ne démarrer aucun serveur HTTP avec le profil `import-robots`.
- Ne pas ajouter de planification récurrente ni de droit d’administration d’index.
- Rédiger les commits en français avec `Jerome Villiseck <jvk@abes.fr>`.

---

## Structure des fichiers

### Domaine de référencement existant

- `ReferencementPageType.java` : ajoute la déduction d’un type depuis un identifiant canonique.
- `ReferencementWriteService.java` : ajoute la création conditionnelle en réutilisant validation, horloge et construction du document.
- `ReferencementWriteServiceTest.java` : protège le nouveau contrat métier et l’absence d’écrasement.

### Import du robots.txt

Les nouveaux composants vivent dans
`fr.abes.thesesapiindexation.referencement.robots` :

- `RobotsTxtEntry.java` : identifiant valide et type déduit.
- `RobotsTxtParseResult.java` : entrées uniques et compteurs du parseur.
- `RobotsTxtParser.java` : analyse pure, stricte et dédupliquante.
- `RobotsTxtSource.java` : port de téléchargement complet.
- `RobotsTxtSourceException.java` : erreur contextualisée de téléchargement.
- `RobotsTxtProperties.java` : URL et délais HTTP.
- `HttpRobotsTxtSource.java` : adaptateur fondé sur `java.net.http.HttpClient`.
- `RobotsTxtImportReport.java` : compteurs complets de lecture et d’écriture.
- `RobotsTxtImportException.java` : identifiant en échec et bilan partiel.
- `RobotsTxtImporter.java` : orchestration source, parseur et service métier.
- `RobotsTxtImportRunner.java` : lancement ponctuel et journalisation.
- `RobotsTxtImportConfiguration.java` : beans réservés à `import-robots`.

### Profils et exploitation

- `application-import-robots.properties` : absence de serveur et valeurs HTTP par défaut.
- `ReferencementController.java` : exclusion du profil d’import.
- `ThesesApiIndexationApplication.java` : arrêt automatique des deux profils ponctuels.
- `README.md` : commande d’exécution, configuration et comportement de reprise.

### Tests

- `RobotsTxtParserTest.java` : formats, filtrage, déduplication et compteurs.
- `RobotsTxtImporterTest.java` : documents produits, préservation, échec partiel et relance.
- `HttpRobotsTxtSourceTest.java` : succès, statut HTTP invalide et timeout.
- `RobotsTxtImportProfileTest.java` : activation des composants et séparation des profils.
- `RobotsTxtImportRunnerTest.java` : bilan journalisé.
- `ThesesApiIndexationApplicationTest.java` : détection des profils ponctuels.
- `ReferencementIndexIntegrationTest.java` : exécution réelle du job contre Elasticsearch.

---

### Task 1: Centraliser la déduction du type et la création conditionnelle

**Files:**
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java`
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java`

**Interfaces:**
- Produces: `ReferencementPageType.fromIdentifier(String): Optional<ReferencementPageType>`.
- Produces: `ReferencementWriteService.createIfAbsent(String, ReferencementWriteCommand): boolean`.
- Consumes: `ReferencementDocumentGateway.createIfAbsent(String, ReferencementDocument): boolean`.
- Guarantees: validation avant accès au gateway et construction identique à celle de `write`.

- [ ] **Step 1: Écrire les tests rouges de déduction des trois types**

Ajouter à `ReferencementWriteServiceTest` :

```java
@ParameterizedTest
@MethodSource("validIdentifiers")
void deduitLeTypeDepuisUnIdentifiantCanonique(
        ReferencementPageType expected,
        String id
) {
    assertThat(ReferencementPageType.fromIdentifier(id))
            .contains(expected);
}

@Test
void neDeduitAucunTypePourUnIdentifiantInvalide() {
    assertThat(ReferencementPageType.fromIdentifier("2024AIXM0640.bib"))
            .isEmpty();
}
```

- [ ] **Step 2: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest#deduitLeTypeDepuisUnIdentifiantCanonique+neDeduitAucunTypePourUnIdentifiantInvalide" test
```

Expected: compilation en échec, car `fromIdentifier` n’existe pas.

- [ ] **Step 3: Implémenter la déduction centralisée**

Ajouter les imports et la méthode dans `ReferencementPageType` :

```java
import java.util.Arrays;
import java.util.Optional;

public static Optional<ReferencementPageType> fromIdentifier(
        String identifier
) {
    return Arrays.stream(values())
            .filter(pageType -> pageType.accepts(identifier))
            .findFirst();
}
```

- [ ] **Step 4: Vérifier les tests de déduction**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest#deduitLeTypeDepuisUnIdentifiantCanonique+neDeduitAucunTypePourUnIdentifiantInvalide" test
```

Expected: les quatre cas paramétrés et le cas invalide passent.

- [ ] **Step 5: Écrire le test rouge de création conditionnelle**

Ajouter :

```java
@Test
void creeUnDocumentImporteUniquementSilEstAbsent() {
    RecordingGateway gateway = new RecordingGateway();
    ReferencementWriteService service = new ReferencementWriteService(
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );
    ReferencementWriteCommand command = new ReferencementWriteCommand(
            ReferencementPageType.PERSONNE,
            true,
            "IMPORT-ROBOTS-INITIAL",
            "robots.txt-importer"
    );

    boolean created = service.createIfAbsent("270350292", command);

    assertThat(created).isTrue();
    assertThat(gateway.createdId).isEqualTo("270350292");
    assertThat(gateway.createdDocument).isEqualTo(
            new ReferencementDocument(
                    ReferencementPageType.PERSONNE,
                    true,
                    "IMPORT-ROBOTS-INITIAL",
                    "robots.txt-importer",
                    NOW
            )
    );
    assertThat(gateway.findCount).isZero();
    assertThat(gateway.saveCount).isZero();
}
```

Compléter `RecordingGateway` avec les champs suivants et remplacer son
implémentation actuelle de `createIfAbsent` par celle-ci :

```java
private String createdId;
private ReferencementDocument createdDocument;
private boolean createResult = true;

@Override
public boolean createIfAbsent(
        String id,
        ReferencementDocument document
) {
    createdId = id;
    createdDocument = document;
    return createResult;
}
```

- [ ] **Step 6: Ajouter le test rouge de préservation**

Ajouter :

```java
@Test
void signaleUnDocumentExistantSansLeModifier() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.createResult = false;
    ReferencementWriteService service = new ReferencementWriteService(
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    boolean created = service.createIfAbsent(
            "270350292",
            new ReferencementWriteCommand(
                    ReferencementPageType.PERSONNE,
                    true,
                    "IMPORT-ROBOTS-INITIAL",
                    "robots.txt-importer"
            )
    );

    assertThat(created).isFalse();
    assertThat(gateway.findCount).isZero();
    assertThat(gateway.saveCount).isZero();
}
```

- [ ] **Step 7: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest#creeUnDocumentImporteUniquementSilEstAbsent+signaleUnDocumentExistantSansLeModifier" test
```

Expected: compilation en échec, car le service n’expose pas
`createIfAbsent`.

- [ ] **Step 8: Factoriser validation et construction du document**

Modifier `ReferencementWriteService` :

```java
public ReferencementWriteResult write(
        String id,
        ReferencementWriteCommand command
) {
    validate(id, command);

    var existingDocument = gateway.findById(id);
    if (existingDocument
            .filter(document -> hasSameRequestedState(document, command))
            .isPresent()) {
        return new ReferencementWriteResult(
                id,
                existingDocument.orElseThrow()
        );
    }

    ReferencementDocument document = toDocument(command);
    gateway.save(id, document);
    return new ReferencementWriteResult(id, document);
}

public boolean createIfAbsent(
        String id,
        ReferencementWriteCommand command
) {
    validate(id, command);
    return gateway.createIfAbsent(id, toDocument(command));
}

private void validate(
        String id,
        ReferencementWriteCommand command
) {
    if (!command.pageType().accepts(id)) {
        throw new ReferencementValidationException(
                id,
                command.pageType()
        );
    }
}

private ReferencementDocument toDocument(
        ReferencementWriteCommand command
) {
    return new ReferencementDocument(
            command.pageType(),
            command.noIndex(),
            command.demandeRef(),
            command.updatedBy(),
            clock.instant()
    );
}
```

Retirer de `write` l’ancienne validation et l’ancienne construction devenues
dupliquées.

- [ ] **Step 9: Vérifier le service complet**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest" test
```

Expected: tous les tests du service passent, y compris ceux de l’API existante.

- [ ] **Step 10: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementPageType.java src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteService.java src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementWriteServiceTest.java
git commit -m "feat: créer un référencement uniquement s’il est absent"
```

---

### Task 2: Parser strictement et dédupliquer le robots.txt

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtEntry.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParseResult.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParser.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParserTest.java`

**Interfaces:**
- Produces: `RobotsTxtParser.parse(String): RobotsTxtParseResult`.
- Produces: `RobotsTxtParseResult.entries(): List<RobotsTxtEntry>`.
- Produces: compteurs `totalLines`, `valid`, `duplicates`, `ignored` et `invalid`.
- Consumes: `ReferencementPageType.fromIdentifier(String)`.

- [ ] **Step 1: Écrire le test rouge du fichier représentatif**

Créer `RobotsTxtParserTest.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtParserTest {

    private final RobotsTxtParser parser = new RobotsTxtParser();

    @Test
    void extraitLesTroisTypesEtProduitDesCompteursExacts() {
        String content = String.join("\r\n", List.of(
                "User-agent: *",
                "",
                "Disallow: /2024AIXM0640",
                "Disallow: /270350292",
                "Disallow: /s233841",
                "Disallow: /2024AIXM0640",
                "Disallow: /2024AIXM0640.bib",
                "Disallow: /recherche/2024AIXM0640",
                "Disallow: /api",
                "Disallow: /2024BAD",
                "Allow: /270350292",
                "# décision historique"
        ));

        RobotsTxtParseResult result = parser.parse(content);

        assertThat(result.entries()).containsExactly(
                new RobotsTxtEntry(
                        "2024AIXM0640",
                        ReferencementPageType.THESE_SOUTENUE
                ),
                new RobotsTxtEntry(
                        "270350292",
                        ReferencementPageType.PERSONNE
                ),
                new RobotsTxtEntry(
                        "s233841",
                        ReferencementPageType.THESE_EN_PREPARATION
                )
        );
        assertThat(result.totalLines()).isEqualTo(12);
        assertThat(result.valid()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.ignored()).isEqualTo(7);
        assertThat(result.invalid()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Écrire les tests rouges de tolérance et d’invalidité**

Ajouter :

```java
@Test
void tolereLesEspacesAutourDeLaDirective() {
    RobotsTxtParseResult result =
            parser.parse("  Disallow :   /14424943X   ");

    assertThat(result.entries()).containsExactly(
            new RobotsTxtEntry(
                    "14424943X",
                    ReferencementPageType.PERSONNE
            )
    );
    assertThat(result.valid()).isEqualTo(1);
}

@Test
void compteLesCandidatsMalformedSansLesImporter() {
    RobotsTxtParseResult result = parser.parse(String.join("\n", List.of(
            "Disallow: /2024AIXM064",
            "Disallow: /27035029Y",
            "Disallow: /s233841A"
    )));

    assertThat(result.entries()).isEmpty();
    assertThat(result.invalid()).isEqualTo(3);
    assertThat(result.ignored()).isZero();
}
```

- [ ] **Step 3: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtParserTest" test
```

Expected: compilation en échec, car le parseur et ses résultats n’existent pas.

- [ ] **Step 4: Créer les modèles immuables**

Créer `RobotsTxtEntry.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;

public record RobotsTxtEntry(
        String identifier,
        ReferencementPageType pageType
) {
}
```

Créer `RobotsTxtParseResult.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import java.util.List;

public record RobotsTxtParseResult(
        List<RobotsTxtEntry> entries,
        int totalLines,
        int duplicates,
        int ignored,
        int invalid
) {
    public RobotsTxtParseResult {
        entries = List.copyOf(entries);
    }

    public int valid() {
        return entries.size();
    }
}
```

- [ ] **Step 5: Implémenter le parseur minimal**

Créer `RobotsTxtParser.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class RobotsTxtParser {

    private static final Pattern ROOT_DISALLOW = Pattern.compile(
            "^\\s*Disallow\\s*:\\s*/([^/\\s]+)\\s*$"
    );
    private static final Pattern IDENTIFIER_CANDIDATE = Pattern.compile(
            "(?:[0-9]{4}.*|[0-9]{8,}.*|s[0-9]+.*)"
    );

    public RobotsTxtParseResult parse(String content) {
        Objects.requireNonNull(content, "content");
        var lines = content.lines().toList();
        Map<String, RobotsTxtEntry> entries = new LinkedHashMap<>();
        int duplicates = 0;
        int ignored = 0;
        int invalid = 0;

        for (String line : lines) {
            var matcher = ROOT_DISALLOW.matcher(line);
            if (!matcher.matches()) {
                ignored++;
                continue;
            }

            String identifier = matcher.group(1);
            if (identifier.contains(".")) {
                ignored++;
                continue;
            }

            var pageType =
                    ReferencementPageType.fromIdentifier(identifier);
            if (pageType.isPresent()) {
                RobotsTxtEntry previous = entries.putIfAbsent(
                        identifier,
                        new RobotsTxtEntry(identifier, pageType.orElseThrow())
                );
                if (previous != null) {
                    duplicates++;
                }
            } else if (IDENTIFIER_CANDIDATE
                    .matcher(identifier)
                    .matches()) {
                invalid++;
            } else {
                ignored++;
            }
        }

        return new RobotsTxtParseResult(
                entries.values().stream().toList(),
                lines.size(),
                duplicates,
                ignored,
                invalid
        );
    }
}
```

- [ ] **Step 6: Vérifier les tests du parseur**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtParserTest" test
```

Expected: tous les tests passent avec les compteurs exacts.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtEntry.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParseResult.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParser.java src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtParserTest.java
git commit -m "feat: analyser strictement le robots.txt"
```

---

### Task 3: Orchestrer un import idempotent et son bilan

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtSource.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportReport.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportException.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImporter.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImporterTest.java`

**Interfaces:**
- Consumes: `RobotsTxtSource.download(): String`.
- Consumes: `RobotsTxtParser.parse(String)`.
- Consumes: `ReferencementWriteService.createIfAbsent(String, ReferencementWriteCommand)`.
- Produces: `RobotsTxtImporter.importInitial(): RobotsTxtImportReport`.
- Produces: `RobotsTxtImportException.failedIdentifier()` et `report()`.

- [ ] **Step 1: Écrire le test rouge des créations et du document exact**

Créer `RobotsTxtImporterTest.java` avec un gateway mémoire :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotsTxtImporterTest {

    private static final Instant NOW =
            Instant.parse("2026-07-28T12:00:00Z");

    @Test
    void creeLesDocumentsValidesEtRetourneLeBilan() {
        MemoryGateway gateway = new MemoryGateway();
        RobotsTxtImporter importer = importer(
                () -> String.join("\n",
                        "User-agent: *",
                        "Disallow: /2024AIXM0640",
                        "Disallow: /270350292",
                        "Disallow: /s233841",
                        "Disallow: /2024AIXM0640"
                ),
                gateway
        );

        RobotsTxtImportReport report = importer.importInitial();

        assertThat(report).isEqualTo(new RobotsTxtImportReport(
                5, 3, 1, 1, 0, 3, 0
        ));
        assertThat(gateway.documents.get("270350292")).isEqualTo(
                new ReferencementDocument(
                        ReferencementPageType.PERSONNE,
                        true,
                        "IMPORT-ROBOTS-INITIAL",
                        "robots.txt-importer",
                        NOW
                )
        );
    }

    private RobotsTxtImporter importer(
            RobotsTxtSource source,
            MemoryGateway gateway
    ) {
        return new RobotsTxtImporter(
                source,
                new RobotsTxtParser(),
                new ReferencementWriteService(
                        gateway,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
    }

    private static final class MemoryGateway
            implements ReferencementDocumentGateway {
        private final Map<String, ReferencementDocument> documents =
                new LinkedHashMap<>();
        private String failOn;

        @Override
        public Optional<ReferencementDocument> findById(String id) {
            return Optional.ofNullable(documents.get(id));
        }

        @Override
        public void save(String id, ReferencementDocument document) {
            documents.put(id, document);
        }

        @Override
        public boolean createIfAbsent(
                String id,
                ReferencementDocument document
        ) {
            if (id.equals(failOn)) {
                throw new ReferencementDocumentAccessException(
                        "Échec simulé pour " + id,
                        new IllegalStateException("indisponible")
                );
            }
            return documents.putIfAbsent(id, document) == null;
        }
    }
}
```

- [ ] **Step 2: Ajouter le test rouge de préservation d’un `noIndex=false`**

Ajouter :

```java
@Test
void conserveUnDocumentExistant() {
    MemoryGateway gateway = new MemoryGateway();
    ReferencementDocument existing = new ReferencementDocument(
            ReferencementPageType.PERSONNE,
            false,
            "ABESSTP-12345",
            "agent@abes.fr",
            Instant.parse("2026-07-27T08:00:00Z")
    );
    gateway.documents.put("270350292", existing);
    RobotsTxtImporter importer =
            importer(() -> "Disallow: /270350292", gateway);

    RobotsTxtImportReport report = importer.importInitial();

    assertThat(report.created()).isZero();
    assertThat(report.existing()).isEqualTo(1);
    assertThat(gateway.documents.get("270350292")).isEqualTo(existing);
}
```

- [ ] **Step 3: Ajouter les tests rouges d’échec partiel et de relance**

Ajouter :

```java
@Test
void exposeLeBilanPartielEtLIdentifiantEnEchec() {
    MemoryGateway gateway = new MemoryGateway();
    gateway.failOn = "270350292";
    RobotsTxtImporter importer = importer(
            () -> String.join("\n",
                    "Disallow: /2024AIXM0640",
                    "Disallow: /270350292",
                    "Disallow: /s233841"
            ),
            gateway
    );

    assertThatThrownBy(importer::importInitial)
            .isInstanceOfSatisfying(
                    RobotsTxtImportException.class,
                    exception -> {
                        assertThat(exception.failedIdentifier())
                                .isEqualTo("270350292");
                        assertThat(exception.report().created())
                                .isEqualTo(1);
                        assertThat(exception.report().existing())
                                .isZero();
                    }
            );
    assertThat(gateway.documents)
            .containsKey("2024AIXM0640")
            .doesNotContainKeys("270350292", "s233841");

    gateway.failOn = null;
    RobotsTxtImportReport retry = importer.importInitial();

    assertThat(retry.created()).isEqualTo(2);
    assertThat(retry.existing()).isEqualTo(1);
    assertThat(gateway.documents).containsKeys(
            "2024AIXM0640",
            "270350292",
            "s233841"
    );
}

@Test
void necritRienQuandLaSourceEchoue() {
    MemoryGateway gateway = new MemoryGateway();
    RobotsTxtImporter importer = importer(
            () -> {
                throw new IllegalStateException("source indisponible");
            },
            gateway
    );

    assertThatThrownBy(importer::importInitial)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("source indisponible");
    assertThat(gateway.documents).isEmpty();
}
```

- [ ] **Step 4: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtImporterTest" test
```

Expected: compilation en échec, car les composants d’import n’existent pas.

- [ ] **Step 5: Créer le port source et le bilan**

Créer `RobotsTxtSource.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

@FunctionalInterface
public interface RobotsTxtSource {
    String download();
}
```

Créer `RobotsTxtImportReport.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

public record RobotsTxtImportReport(
        int totalLines,
        int valid,
        int duplicates,
        int ignored,
        int invalid,
        int created,
        int existing
) {
    static RobotsTxtImportReport from(
            RobotsTxtParseResult parsed,
            int created,
            int existing
    ) {
        return new RobotsTxtImportReport(
                parsed.totalLines(),
                parsed.valid(),
                parsed.duplicates(),
                parsed.ignored(),
                parsed.invalid(),
                created,
                existing
        );
    }
}
```

Créer `RobotsTxtImportException.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

public class RobotsTxtImportException extends RuntimeException {

    private final String failedIdentifier;
    private final RobotsTxtImportReport report;

    public RobotsTxtImportException(
            String failedIdentifier,
            RobotsTxtImportReport report,
            Throwable cause
    ) {
        super(
                "Échec de l’import robots.txt pour "
                        + failedIdentifier
                        + " après "
                        + report.created()
                        + " création(s) et "
                        + report.existing()
                        + " document(s) existant(s)",
                cause
        );
        this.failedIdentifier = failedIdentifier;
        this.report = report;
    }

    public String failedIdentifier() {
        return failedIdentifier;
    }

    public RobotsTxtImportReport report() {
        return report;
    }
}
```

- [ ] **Step 6: Implémenter l’orchestrateur**

Créer `RobotsTxtImporter.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementWriteCommand;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;

public class RobotsTxtImporter {

    private static final String DEMANDE_REF = "IMPORT-ROBOTS-INITIAL";
    private static final String UPDATED_BY = "robots.txt-importer";

    private final RobotsTxtSource source;
    private final RobotsTxtParser parser;
    private final ReferencementWriteService writeService;

    public RobotsTxtImporter(
            RobotsTxtSource source,
            RobotsTxtParser parser,
            ReferencementWriteService writeService
    ) {
        this.source = source;
        this.parser = parser;
        this.writeService = writeService;
    }

    public RobotsTxtImportReport importInitial() {
        String content = source.download();
        RobotsTxtParseResult parsed = parser.parse(content);
        int created = 0;
        int existing = 0;

        for (RobotsTxtEntry entry : parsed.entries()) {
            try {
                boolean wasCreated = writeService.createIfAbsent(
                        entry.identifier(),
                        new ReferencementWriteCommand(
                                entry.pageType(),
                                true,
                                DEMANDE_REF,
                                UPDATED_BY
                        )
                );
                if (wasCreated) {
                    created++;
                } else {
                    existing++;
                }
            } catch (RuntimeException exception) {
                throw new RobotsTxtImportException(
                        entry.identifier(),
                        RobotsTxtImportReport.from(
                                parsed,
                                created,
                                existing
                        ),
                        exception
                );
            }
        }

        return RobotsTxtImportReport.from(parsed, created, existing);
    }
}
```

- [ ] **Step 7: Vérifier l’orchestrateur**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtImporterTest" test
```

Expected: tous les tests passent, y compris la relance après import partiel.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtSource.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportReport.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportException.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImporter.java src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImporterTest.java
git commit -m "feat: orchestrer l’import initial du robots.txt"
```

---

### Task 4: Télécharger entièrement la source HTTP

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtProperties.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtSourceException.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/HttpRobotsTxtSource.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/robots/HttpRobotsTxtSourceTest.java`

**Interfaces:**
- Consumes: `RobotsTxtProperties(URI, Duration, Duration)`.
- Produces: `HttpRobotsTxtSource.download(): String`.
- Guarantees: seuls les statuts `2xx` retournent du contenu.
- Guarantees: interruption restaurée et erreurs traduites en `RobotsTxtSourceException`.

- [ ] **Step 1: Écrire le test rouge d’une réponse réussie**

Créer `HttpRobotsTxtSourceTest.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRobotsTxtSourceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void telechargeIntegralementUneReponseDeuxCents()
            throws IOException {
        String body = "User-agent: *\nDisallow: /270350292";
        server = server(200, body, Duration.ZERO);
        HttpRobotsTxtSource source = source(Duration.ofSeconds(1));

        assertThat(source.download()).isEqualTo(body);
    }

    private HttpServer server(
            int status,
            String body,
            Duration delay
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        httpServer.createContext("/robots.txt", exchange -> {
            try {
                Thread.sleep(delay.toMillis());
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer;
    }

    private HttpRobotsTxtSource source(Duration readTimeout) {
        URI url = URI.create(
                "http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/robots.txt"
        );
        RobotsTxtProperties properties = new RobotsTxtProperties(
                url,
                Duration.ofSeconds(1),
                readTimeout
        );
        return new HttpRobotsTxtSource(
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .build(),
                properties
        );
    }
}
```

- [ ] **Step 2: Ajouter les tests rouges de statut et de timeout**

Ajouter :

```java
@Test
void refuseUnStatutHttpNonValide() throws IOException {
    server = server(503, "indisponible", Duration.ZERO);

    assertThatThrownBy(() -> source(Duration.ofSeconds(1)).download())
            .isInstanceOf(RobotsTxtSourceException.class)
            .hasMessageContaining("503");
}

@Test
void echoueQuandLeDelaiDeLectureEstDepasse()
        throws IOException {
    server = server(
            200,
            "Disallow: /270350292",
            Duration.ofMillis(250)
    );

    assertThatThrownBy(() -> source(Duration.ofMillis(50)).download())
            .isInstanceOf(RobotsTxtSourceException.class)
            .hasMessageContaining("télécharger");
}
```

- [ ] **Step 3: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=HttpRobotsTxtSourceTest" test
```

Expected: compilation en échec, car l’adaptateur HTTP n’existe pas.

- [ ] **Step 4: Créer les propriétés et l’exception**

Créer `RobotsTxtProperties.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "referencement.robots")
public record RobotsTxtProperties(
        URI url,
        Duration connectTimeout,
        Duration readTimeout
) {
}
```

Créer `RobotsTxtSourceException.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

public class RobotsTxtSourceException extends RuntimeException {

    public RobotsTxtSourceException(String message) {
        super(message);
    }

    public RobotsTxtSourceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Implémenter le téléchargement complet**

Créer `HttpRobotsTxtSource.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpRobotsTxtSource implements RobotsTxtSource {

    private final HttpClient client;
    private final RobotsTxtProperties properties;

    public HttpRobotsTxtSource(
            HttpClient client,
            RobotsTxtProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String download() {
        HttpRequest request = HttpRequest.newBuilder(properties.url())
                .timeout(properties.readTimeout())
                .header("Accept", "text/plain")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new RobotsTxtSourceException(
                        "Le téléchargement du robots.txt a retourné le statut "
                                + response.statusCode()
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw downloadFailure(exception);
        } catch (IOException exception) {
            throw downloadFailure(exception);
        }
    }

    private RobotsTxtSourceException downloadFailure(Exception cause) {
        return new RobotsTxtSourceException(
                "Impossible de télécharger le robots.txt depuis "
                        + properties.url(),
                cause
        );
    }
}
```

- [ ] **Step 6: Vérifier le client HTTP**

Run:

```powershell
mvn --batch-mode "-Dtest=HttpRobotsTxtSourceTest" test
```

Expected: les trois scénarios passent sans dépendance Maven supplémentaire.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtProperties.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtSourceException.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/HttpRobotsTxtSource.java src/test/java/fr/abes/thesesapiindexation/referencement/robots/HttpRobotsTxtSourceTest.java
git commit -m "feat: télécharger la source du robots.txt"
```

---

### Task 5: Activer le job avec un profil ponctuel

**Files:**
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportRunner.java`
- Create: `src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportConfiguration.java`
- Create: `src/main/resources/application-import-robots.properties`
- Modify: `src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementController.java`
- Modify: `src/main/java/fr/abes/thesesapiindexation/ThesesApiIndexationApplication.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportProfileTest.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportRunnerTest.java`
- Test: `src/test/java/fr/abes/thesesapiindexation/ThesesApiIndexationApplicationTest.java`

**Interfaces:**
- Produces: profil `import-robots`.
- Produces: propriétés `referencement.robots.url`, `connect-timeout` et `read-timeout`.
- Produces: `RobotsTxtImportRunner.run(ApplicationArguments)`.
- Guarantees: pas de contrôleur ni de serveur HTTP pendant l’import.
- Guarantees: arrêt automatique pour `init-index` ou `import-robots`, jamais pour le profil normal.

- [ ] **Step 1: Écrire les tests rouges d’activation du profil**

Créer `RobotsTxtImportProfileTest.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtImportProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            RobotsTxtImportConfiguration.class
                    )
                    .withBean(
                            RobotsTxtSource.class,
                            () -> () -> ""
                    )
                    .withBean(
                            ReferencementWriteService.class,
                            RobotsTxtImportProfileTest::writeService
                    )
                    .withPropertyValues(
                            "referencement.robots.url=https://theses.fr/robots.txt",
                            "referencement.robots.connect-timeout=5s",
                            "referencement.robots.read-timeout=30s"
                    );

    @Test
    void chargeLeJobUniquementAvecLeProfilImportRobots() {
        contextRunner.run(context -> {
            assertThat(context)
                    .doesNotHaveBean(RobotsTxtImporter.class);
            assertThat(context)
                    .doesNotHaveBean(ApplicationRunner.class);
        });

        contextRunner
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("import-robots"))
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            RobotsTxtParser.class
                    );
                    assertThat(context).hasSingleBean(
                            RobotsTxtImporter.class
                    );
                    assertThat(context).hasSingleBean(
                            ApplicationRunner.class
                    );
                });
    }

    @Test
    void nexposePasLeControleurPendantLImport() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReferencementController.class)
                .withBean(
                        ReferencementWriteService.class,
                        RobotsTxtImportProfileTest::writeService
                )
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("import-robots"))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ReferencementController.class));
    }

    @Test
    void desactiveLeServeurHttpDansLeProfil() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(
                "application-import-robots.properties"
        ).getInputStream()) {
            properties.load(input);
        }

        assertThat(properties.getProperty(
                "spring.main.web-application-type"
        )).isEqualTo("none");
    }

    private static ReferencementWriteService writeService() {
        ReferencementDocumentGateway gateway =
                new ReferencementDocumentGateway() {
                    @Override
                    public Optional<ReferencementDocument> findById(
                            String id
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public void save(
                            String id,
                            ReferencementDocument document
                    ) {
                    }

                    @Override
                    public boolean createIfAbsent(
                            String id,
                            ReferencementDocument document
                    ) {
                        return true;
                    }
                };
        return new ReferencementWriteService(
                gateway,
                Clock.fixed(
                        Instant.parse("2026-07-28T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }
}
```

- [ ] **Step 2: Écrire le test rouge du bilan journalisé**

Créer `RobotsTxtImportRunnerTest.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RobotsTxtImportRunnerTest {

    @Test
    void journaliseTousLesCompteurs(CapturedOutput output)
            throws Exception {
        ReferencementDocumentGateway gateway =
                new ReferencementDocumentGateway() {
                    @Override
                    public Optional<ReferencementDocument> findById(
                            String id
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public void save(
                            String id,
                            ReferencementDocument document
                    ) {
                    }

                    @Override
                    public boolean createIfAbsent(
                            String id,
                            ReferencementDocument document
                    ) {
                        return true;
                    }
                };
        RobotsTxtImporter importer = new RobotsTxtImporter(
                () -> "Disallow: /270350292",
                new RobotsTxtParser(),
                new ReferencementWriteService(
                        gateway,
                        Clock.systemUTC()
                )
        );

        new RobotsTxtImportRunner(importer).run(
                new DefaultApplicationArguments()
        );

        assertThat(output).contains(
                "lignes=1, valides=1, doublons=0, ignorées=0, "
                        + "invalides=0, créés=1, existants=0"
        );
    }
}
```

- [ ] **Step 3: Écrire le test rouge des profils ponctuels**

Créer `ThesesApiIndexationApplicationTest.java` :

```java
package fr.abes.thesesapiindexation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ThesesApiIndexationApplicationTest {

    @Test
    void reconnaitUniquementLesProfilsPonctuels() {
        MockEnvironment normal = new MockEnvironment();
        MockEnvironment init = new MockEnvironment();
        init.setActiveProfiles("init-index");
        MockEnvironment importRobots = new MockEnvironment();
        importRobots.setActiveProfiles("import-robots");

        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(normal)).isFalse();
        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(init)).isTrue();
        assertThat(ThesesApiIndexationApplication
                .isOneShotProfile(importRobots)).isTrue();
    }
}
```

- [ ] **Step 4: Vérifier l’échec attendu**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtImportProfileTest,RobotsTxtImportRunnerTest,ThesesApiIndexationApplicationTest" test
```

Expected: compilation en échec, car la configuration, le runner et la méthode
de détection n’existent pas.

- [ ] **Step 5: Créer le runner et son journal structuré**

Créer `RobotsTxtImportRunner.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class RobotsTxtImportRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RobotsTxtImportRunner.class);

    private final RobotsTxtImporter importer;

    public RobotsTxtImportRunner(RobotsTxtImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            RobotsTxtImportReport report = importer.importInitial();
            LOGGER.info(
                    "Import robots.txt terminé : lignes={}, valides={}, "
                            + "doublons={}, ignorées={}, invalides={}, "
                            + "créés={}, existants={}",
                    report.totalLines(),
                    report.valid(),
                    report.duplicates(),
                    report.ignored(),
                    report.invalid(),
                    report.created(),
                    report.existing()
            );
        } catch (RobotsTxtImportException exception) {
            RobotsTxtImportReport report = exception.report();
            LOGGER.error(
                    "Import robots.txt interrompu sur {} : lignes={}, "
                            + "valides={}, doublons={}, ignorées={}, "
                            + "invalides={}, créés={}, existants={}",
                    exception.failedIdentifier(),
                    report.totalLines(),
                    report.valid(),
                    report.duplicates(),
                    report.ignored(),
                    report.invalid(),
                    report.created(),
                    report.existing()
            );
            throw exception;
        }
    }
}
```

- [ ] **Step 6: Créer la configuration du profil**

Créer `RobotsTxtImportConfiguration.java` :

```java
package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@Profile("import-robots")
@EnableConfigurationProperties(RobotsTxtProperties.class)
public class RobotsTxtImportConfiguration {

    @Bean
    HttpClient robotsTxtHttpClient(RobotsTxtProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(RobotsTxtSource.class)
    RobotsTxtSource robotsTxtSource(
            HttpClient robotsTxtHttpClient,
            RobotsTxtProperties properties
    ) {
        return new HttpRobotsTxtSource(
                robotsTxtHttpClient,
                properties
        );
    }

    @Bean
    RobotsTxtParser robotsTxtParser() {
        return new RobotsTxtParser();
    }

    @Bean
    RobotsTxtImporter robotsTxtImporter(
            RobotsTxtSource source,
            RobotsTxtParser parser,
            ReferencementWriteService writeService
    ) {
        return new RobotsTxtImporter(source, parser, writeService);
    }

    @Bean
    ApplicationRunner robotsTxtImportRunner(
            RobotsTxtImporter importer
    ) {
        return new RobotsTxtImportRunner(importer);
    }
}
```

- [ ] **Step 7: Configurer l’absence de serveur et les valeurs HTTP**

Créer `application-import-robots.properties` :

```properties
spring.main.web-application-type=none
referencement.robots.url=${ROBOTS_URL:https://theses.fr/robots.txt}
referencement.robots.connect-timeout=${ROBOTS_CONNECT_TIMEOUT:5s}
referencement.robots.read-timeout=${ROBOTS_READ_TIMEOUT:30s}
```

- [ ] **Step 8: Séparer le contrôleur et étendre l’arrêt automatique**

Remplacer le profil du contrôleur :

```java
@Profile("!init-index & !import-robots")
```

Modifier `ThesesApiIndexationApplication` :

```java
import org.springframework.core.env.Environment;

public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(
            ThesesApiIndexationApplication.class,
            args
    );
    if (isOneShotProfile(context.getEnvironment())) {
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }
}

static boolean isOneShotProfile(Environment environment) {
    return environment.acceptsProfiles(
            Profiles.of("init-index | import-robots")
    );
}
```

- [ ] **Step 9: Vérifier les profils et le runner**

Run:

```powershell
mvn --batch-mode "-Dtest=RobotsTxtImportProfileTest,RobotsTxtImportRunnerTest,ThesesApiIndexationApplicationTest,ReferencementControllerTest,ReferencementIndexProfileTest" test
```

Expected: tous les tests passent ; le contrôleur HTTP existant reste actif
hors des profils ponctuels.

- [ ] **Step 10: Commit**

```powershell
git add src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportRunner.java src/main/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportConfiguration.java src/main/resources/application-import-robots.properties src/main/java/fr/abes/thesesapiindexation/referencement/ReferencementController.java src/main/java/fr/abes/thesesapiindexation/ThesesApiIndexationApplication.java src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportProfileTest.java src/test/java/fr/abes/thesesapiindexation/referencement/robots/RobotsTxtImportRunnerTest.java src/test/java/fr/abes/thesesapiindexation/ThesesApiIndexationApplicationTest.java
git commit -m "feat: exécuter l’import avec un profil ponctuel"
```

---

### Task 6: Vérifier le job de bout en bout avec Elasticsearch

**Files:**
- Modify: `src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexIntegrationTest.java`

**Interfaces:**
- Consumes: profil `import-robots`, source HTTP configurable et Elasticsearch sécurisé.
- Guarantees: import réel des trois types, préservation d’un document existant, bilan et code de sortie `0`.

- [ ] **Step 1: Écrire le test d’intégration rouge**

Ajouter les imports :

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
```

Ajouter le test :

```java
@Test
void importeLeRobotsTxtPuisTermineLeProcessus() throws Exception {
    initializer.initialize();
    ReferencementDocumentGateway gateway =
            new ElasticsearchReferencementDocumentGateway(
                    client,
                    INDEX_NAME
            );
    ReferencementDocument existing = new ReferencementDocument(
            ReferencementPageType.PERSONNE,
            false,
            "ABESSTP-12345",
            "agent@abes.fr",
            Instant.parse("2026-07-27T08:00:00Z")
    );
    gateway.save("270350292", existing);

    String robots = String.join("\n", List.of(
            "Disallow: /2024AIXM0640",
            "Disallow: /270350292",
            "Disallow: /s233841",
            "Disallow: /2024AIXM0640",
            "Disallow: /2024AIXM0640.bib"
    ));
    HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0),
            0
    );
    server.createContext("/robots.txt", exchange -> {
        byte[] body = robots.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    });
    server.start();

    try {
        Process process = new ProcessBuilder(
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java"
                ).toString(),
                "-cp",
                System.getProperty("surefire.test.class.path"),
                ThesesApiIndexationApplication.class.getName(),
                "--spring.profiles.active=import-robots",
                "--es.hostname=" + ELASTICSEARCH.getHost(),
                "--es.port=" + ELASTICSEARCH.getMappedPort(9200),
                "--es.protocol=https",
                "--es.username=elastic",
                "--es.password=changeme",
                "--es.ca-certificate=" + caCertificate.toUri(),
                "--referencement.index.name=" + INDEX_NAME,
                "--referencement.robots.url=http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/robots.txt"
        )
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor();
        }
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains(
                "lignes=5, valides=3, doublons=1, ignorées=1, "
                        + "invalides=0, créés=2, existants=1"
        );
        assertThat(gateway.findById("2024AIXM0640"))
                .get()
                .extracting(
                        ReferencementDocument::pageType,
                        ReferencementDocument::noIndex,
                        ReferencementDocument::demandeRef,
                        ReferencementDocument::updatedBy
                )
                .containsExactly(
                        ReferencementPageType.THESE_SOUTENUE,
                        true,
                        "IMPORT-ROBOTS-INITIAL",
                        "robots.txt-importer"
                );
        assertThat(gateway.findById("270350292")).contains(existing);
        assertThat(gateway.findById("s233841"))
                .get()
                .extracting(ReferencementDocument::pageType)
                .isEqualTo(
                        ReferencementPageType.THESE_EN_PREPARATION
                );
    } finally {
        server.stop(0);
    }
}
```

- [ ] **Step 2: Ajouter le test du code de sortie en erreur**

Ajouter :

```java
@Test
void echoueSansEcrireQuandLaSourceHttpEstIndisponible()
        throws Exception {
    initializer.initialize();
    HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0),
            0
    );
    server.createContext("/robots.txt", exchange -> {
        exchange.sendResponseHeaders(503, -1);
        exchange.close();
    });
    server.start();

    try {
        Process process = new ProcessBuilder(
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java"
                ).toString(),
                "-cp",
                System.getProperty("surefire.test.class.path"),
                ThesesApiIndexationApplication.class.getName(),
                "--spring.profiles.active=import-robots",
                "--es.hostname=" + ELASTICSEARCH.getHost(),
                "--es.port=" + ELASTICSEARCH.getMappedPort(9200),
                "--es.protocol=https",
                "--es.username=elastic",
                "--es.password=changeme",
                "--es.ca-certificate=" + caCertificate.toUri(),
                "--referencement.index.name=" + INDEX_NAME,
                "--referencement.robots.url=http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + "/robots.txt"
        )
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly().waitFor();
        }
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isNotZero();
        assertThat(client.count(request -> request.index(INDEX_NAME))
                .count()).isZero();
    } finally {
        server.stop(0);
    }
}
```

- [ ] **Step 3: Exécuter les deux scénarios du profil**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexIntegrationTest#importeLeRobotsTxtPuisTermineLeProcessus+echoueSansEcrireQuandLaSourceHttpEstIndisponible" test
```

Expected: les deux sous-processus se terminent en moins de 20 secondes ; le
premier retourne `0` et le second un code non nul.

- [ ] **Step 4: Exécuter toute l’intégration Elasticsearch**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementIndexIntegrationTest" test
```

Expected: tous les tests Testcontainers passent, y compris l’initialisation
existante et le nouveau job.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/fr/abes/thesesapiindexation/referencement/ReferencementIndexIntegrationTest.java
git commit -m "test: vérifier l’import robots.txt dans Elasticsearch"
```

---

### Task 7: Documenter l’exploitation et valider la tranche complète

**Files:**
- Modify: `README.md`
- Verify: all source and test files

**Interfaces:**
- Consumes: profil, variables HTTP et variables Elasticsearch.
- Produces: procédure d’exécution et de relance exploitable.
- Guarantees: suite Maven complète verte et arbre de travail propre.

- [ ] **Step 1: Ajouter la procédure d’import au README**

Ajouter après la section d’écriture :

````markdown
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
````

- [ ] **Step 2: Lancer les tests unitaires ciblés**

Run:

```powershell
mvn --batch-mode "-Dtest=ReferencementWriteServiceTest,RobotsTxtParserTest,RobotsTxtImporterTest,HttpRobotsTxtSourceTest,RobotsTxtImportProfileTest,RobotsTxtImportRunnerTest,ThesesApiIndexationApplicationTest" test
```

Expected: tous les tests unitaires et de profils passent.

- [ ] **Step 3: Vérifier les dépendances**

Run:

```powershell
mvn --batch-mode dependency:tree "-Dincludes=co.elastic.clients:elasticsearch-java,org.elasticsearch.client:elasticsearch-rest-client"
```

Expected: les deux clients Elasticsearch restent exactement en version
`8.10.3` et aucune nouvelle dépendance HTTP n’apparaît.

- [ ] **Step 4: Lancer le build complet**

Run:

```powershell
mvn --batch-mode clean verify
```

Expected: `BUILD SUCCESS`, aucun échec et aucun test ignoré.

- [ ] **Step 5: Contrôler le diff et les profils**

Run:

```powershell
git diff --check
rg -n "@Profile|web-application-type|referencement.robots" src/main
git status --short
```

Expected:

- aucune erreur d’espace ;
- `ReferencementController` exclut `init-index` et `import-robots` ;
- les propriétés du job se trouvent dans
  `application-import-robots.properties` ;
- seul `README.md` reste non commité à cette étape.

- [ ] **Step 6: Commit**

```powershell
git add README.md
git commit -m "docs: documenter l’import initial du robots.txt"
```

- [ ] **Step 7: Vérifier la branche**

Run:

```powershell
git status --short --branch
git log --oneline --decorate -12
```

Expected: arbre de travail propre sur `SOA-820-referencement`, avec les sept
commits d’implémentation au-dessus du commit de planification.

---

## Critères de fin

- Le profil `import-robots` télécharge par défaut
  `https://theses.fr/robots.txt`.
- Une erreur de téléchargement ne déclenche aucune écriture.
- Seuls les NNT, PPN et numéros de sujet racine valides sont proposés à
  Elasticsearch.
- Les suffixes, sous-chemins, métadonnées, commentaires et lignes vides sont
  ignorés et comptabilisés.
- Les doublons ne produisent qu’une tentative de création.
- Les documents créés contiennent exactement le type déduit, `noIndex: true`,
  `IMPORT-ROBOTS-INITIAL`, `robots.txt-importer` et une date UTC.
- Un document existant, notamment `noIndex: false`, reste inchangé.
- Une erreur Elasticsearch arrête le job et expose le bilan partiel.
- Une relance après échec partiel complète l’import sans écrasement.
- Le bilan final contient les sept compteurs définis dans la spécification.
- Aucun serveur HTTP n’est lancé pendant le job.
- Le processus retourne `0` après succès et un code non nul après échec.
- Les profils normal et `init-index` conservent leur comportement.
- `mvn --batch-mode clean verify` réussit intégralement.
