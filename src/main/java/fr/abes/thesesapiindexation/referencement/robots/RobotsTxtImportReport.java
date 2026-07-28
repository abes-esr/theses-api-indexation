package fr.abes.thesesapiindexation.referencement.robots;

public record RobotsTxtImportReport(
        int totalLines,
        int valid,
        int duplicates,
        int ignored,
        int invalid,
        int created,
        int existing
) {
    static RobotsTxtImportReport from(
            RobotsTxtParseResult parsed,
            int created,
            int existing
    ) {
        return new RobotsTxtImportReport(
                parsed.totalLines(),
                parsed.valid(),
                parsed.duplicates(),
                parsed.ignored(),
                parsed.invalid(),
                created,
                existing
        );
    }
}
