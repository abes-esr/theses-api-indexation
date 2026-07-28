package fr.abes.thesesapiindexation.referencement;

public record ReferencementWriteCommand(
        ReferencementPageType pageType,
        boolean noIndex,
        String demandeRef,
        String updatedBy
) {
}
