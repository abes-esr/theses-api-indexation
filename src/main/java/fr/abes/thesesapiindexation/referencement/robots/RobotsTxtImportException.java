package fr.abes.thesesapiindexation.referencement.robots;

public class RobotsTxtImportException extends RuntimeException {

    private final String failedIdentifier;
    private final RobotsTxtImportReport report;

    public RobotsTxtImportException(
            String failedIdentifier,
            RobotsTxtImportReport report,
            Throwable cause
    ) {
        super(
                "Échec de l’import robots.txt pour "
                        + failedIdentifier
                        + " après "
                        + report.created()
                        + " création(s) et "
                        + report.existing()
                        + " document(s) existant(s)",
                cause
        );
        this.failedIdentifier = failedIdentifier;
        this.report = report;
    }

    public String failedIdentifier() {
        return failedIdentifier;
    }

    public RobotsTxtImportReport report() {
        return report;
    }
}
