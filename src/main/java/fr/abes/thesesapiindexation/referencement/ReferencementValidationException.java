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
