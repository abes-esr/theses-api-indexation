package fr.abes.thesesapiindexation.referencement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReferencementWriteRequest(
        @NotNull ReferencementPageType pageType,
        @NotNull Boolean noIndex,
        @NotBlank @Size(max = 64) String demandeRef
) {

    ReferencementWriteCommand toCommand(String updatedBy) {
        return new ReferencementWriteCommand(
                pageType,
                noIndex,
                demandeRef,
                updatedBy
        );
    }
}
