package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementWriteCommand;
import fr.abes.thesesapiindexation.referencement.ReferencementWriteService;

public class RobotsTxtImporter {

    private static final String DEMANDE_REF = "IMPORT-ROBOTS-INITIAL";
    private static final String UPDATED_BY = "robots.txt-importer";

    private final RobotsTxtSource source;
    private final RobotsTxtParser parser;
    private final ReferencementWriteService writeService;

    public RobotsTxtImporter(
            RobotsTxtSource source,
            RobotsTxtParser parser,
            ReferencementWriteService writeService
    ) {
        this.source = source;
        this.parser = parser;
        this.writeService = writeService;
    }

    public RobotsTxtImportReport importInitial() {
        String content = source.download();
        RobotsTxtParseResult parsed = parser.parse(content);
        int created = 0;
        int existing = 0;

        for (RobotsTxtEntry entry : parsed.entries()) {
            try {
                boolean wasCreated = writeService.createIfAbsent(
                        entry.identifier(),
                        new ReferencementWriteCommand(
                                entry.pageType(),
                                true,
                                DEMANDE_REF,
                                UPDATED_BY
                        )
                );
                if (wasCreated) {
                    created++;
                } else {
                    existing++;
                }
            } catch (RuntimeException exception) {
                throw new RobotsTxtImportException(
                        entry.identifier(),
                        RobotsTxtImportReport.from(
                                parsed,
                                created,
                                existing
                        ),
                        exception
                );
            }
        }

        return RobotsTxtImportReport.from(parsed, created, existing);
    }
}
