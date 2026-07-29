package fr.abes.thesesapiindexation.referencement;

public class ReferencementIndexAlreadyExistsException extends RuntimeException {

    public ReferencementIndexAlreadyExistsException(String indexName) {
        super("L'index " + indexName + " existe déjà");
    }

    public ReferencementIndexAlreadyExistsException(String indexName, Throwable cause) {
        super("L'index " + indexName + " existe déjà", cause);
    }
}
