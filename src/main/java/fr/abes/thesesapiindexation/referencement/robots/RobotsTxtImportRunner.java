package fr.abes.thesesapiindexation.referencement.robots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class RobotsTxtImportRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RobotsTxtImportRunner.class);

    private final RobotsTxtImporter importer;

    public RobotsTxtImportRunner(RobotsTxtImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            RobotsTxtImportReport report = importer.importInitial();
            logReport("Import robots.txt terminé", report, null);
        } catch (RobotsTxtImportException exception) {
            logReport(
                    "Import robots.txt interrompu sur " + exception.failedIdentifier(),
                    exception.report(),
                    exception
            );
            throw exception;
        }
    }

    private void logReport(String message, RobotsTxtImportReport report, Throwable cause) {
        String formatted = message + " : lignes={}, valides={}, doublons={}, ignorées={}, "
                + "invalides={}, créés={}, existants={}";
        if (cause != null) {
            LOGGER.error(formatted,
                    report.totalLines(), report.valid(), report.duplicates(),
                    report.ignored(), report.invalid(), report.created(),
                    report.existing(), cause);
        } else {
            LOGGER.info(formatted,
                    report.totalLines(), report.valid(), report.duplicates(),
                    report.ignored(), report.invalid(), report.created(),
                    report.existing());
        }
    }
}
