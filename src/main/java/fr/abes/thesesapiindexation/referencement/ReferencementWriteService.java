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
}
