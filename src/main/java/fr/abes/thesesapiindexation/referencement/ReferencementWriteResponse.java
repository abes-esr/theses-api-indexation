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
