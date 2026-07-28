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
