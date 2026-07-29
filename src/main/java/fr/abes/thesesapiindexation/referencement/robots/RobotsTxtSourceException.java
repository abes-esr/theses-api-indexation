package fr.abes.thesesapiindexation.referencement.robots;

public class RobotsTxtSourceException extends RuntimeException {

    public RobotsTxtSourceException(String message) {
        super(message);
    }

    public RobotsTxtSourceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
