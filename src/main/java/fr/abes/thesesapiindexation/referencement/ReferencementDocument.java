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
