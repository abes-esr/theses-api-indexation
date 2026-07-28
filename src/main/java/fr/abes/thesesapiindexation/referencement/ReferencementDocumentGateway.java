package fr.abes.thesesapiindexation.referencement;

import java.util.Optional;

public interface ReferencementDocumentGateway {

    Optional<ReferencementDocument> findById(String id);

    void save(String id, ReferencementDocument document);
}
