package fr.abes.thesesapiindexation.referencement;

public class ReferencementDocumentAccessException extends RuntimeException {

    public ReferencementDocumentAccessException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
