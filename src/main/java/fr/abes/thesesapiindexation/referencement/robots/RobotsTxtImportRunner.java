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
            LOGGER.info(
                    "Import robots.txt terminé : lignes={}, valides={}, "
                            + "doublons={}, ignorées={}, invalides={}, "
                            + "créés={}, existants={}",
                    report.totalLines(),
                    report.valid(),
                    report.duplicates(),
                    report.ignored(),
                    report.invalid(),
                    report.created(),
                    report.existing()
            );
        } catch (RobotsTxtImportException exception) {
            RobotsTxtImportReport report = exception.report();
            LOGGER.error(
                    "Import robots.txt interrompu sur {} : lignes={}, "
                            + "valides={}, doublons={}, ignorées={}, "
                            + "invalides={}, créés={}, existants={}",
                    exception.failedIdentifier(),
                    report.totalLines(),
                    report.valid(),
                    report.duplicates(),
                    report.ignored(),
                    report.invalid(),
                    report.created(),
                    report.existing()
            );
            throw exception;
        }
    }
}
