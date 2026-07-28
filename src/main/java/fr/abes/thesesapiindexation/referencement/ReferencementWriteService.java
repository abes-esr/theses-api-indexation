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
        validate(id, command);

        var existingDocument = gateway.findById(id);
        if (existingDocument
                .filter(document -> hasSameRequestedState(document, command))
                .isPresent()) {
            return new ReferencementWriteResult(
                    id,
                    existingDocument.orElseThrow()
            );
        }

        ReferencementDocument document = toDocument(command);
        gateway.save(id, document);
        return new ReferencementWriteResult(id, document);
    }

    public boolean createIfAbsent(
            String id,
            ReferencementWriteCommand command
    ) {
        validate(id, command);
        return gateway.createIfAbsent(id, toDocument(command));
    }

    private void validate(
            String id,
            ReferencementWriteCommand command
    ) {
        if (!command.pageType().accepts(id)) {
            throw new ReferencementValidationException(
                    id,
                    command.pageType()
            );
        }
    }

    private ReferencementDocument toDocument(
            ReferencementWriteCommand command
    ) {
        return new ReferencementDocument(
                command.pageType(),
                command.noIndex(),
                command.demandeRef(),
                command.updatedBy(),
                clock.instant()
        );
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
