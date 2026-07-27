package fr.abes.thesesapiindexation.referencement;

public class ReferencementIndexAlreadyExistsException extends RuntimeException {

    public ReferencementIndexAlreadyExistsException(String indexName) {
        super("L'index " + indexName + " existe déjà");
    }
}
