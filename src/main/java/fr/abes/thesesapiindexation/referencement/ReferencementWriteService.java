package fr.abes.thesesapiindexation.referencement;

import java.time.Clock;

public class ReferencementWriteService {
    private final ReferencementDocumentGateway gateway;
    private final Clock clock;

    public ReferencementWriteService(
            ReferencementDocumentGateway gateway,
            Clock clock
    ) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public ReferencementWriteResult write(
            String id,
            ReferencementWriteCommand command
    ) {
        var existingDocument = gateway.findById(id);
        if (existingDocument
                .filter(document -> hasSameRequestedState(document, command))
                .isPresent()) {
            return new ReferencementWriteResult(
                    id,
                    existingDocument.orElseThrow()
            );
        }

        ReferencementDocument document = new ReferencementDocument(
                command.pageType(),
                command.noIndex(),
                command.demandeRef(),
                command.updatedBy(),
                clock.instant()
        );
        gateway.save(id, document);
        return new ReferencementWriteResult(id, document);
    }

    private boolean hasSameRequestedState(
            ReferencementDocument document,
            ReferencementWriteCommand command
    ) {
        return document.pageType() == command.pageType()
                && document.noIndex() == command.noIndex()
                && document.demandeRef().equals(command.demandeRef())
                && document.updatedBy().equals(command.updatedBy());
    }
}
