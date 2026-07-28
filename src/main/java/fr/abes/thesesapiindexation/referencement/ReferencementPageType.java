package fr.abes.thesesapiindexation.referencement;

import java.util.regex.Pattern;

public enum ReferencementPageType {

    THESE_SOUTENUE("[0-9]{4}[A-Z0-9]{8}"),
    PERSONNE("[0-9]{8}[0-9X]"),
    THESE_EN_PREPARATION("s[0-9]+");

    private final Pattern identifierPattern;

    ReferencementPageType(String identifierPattern) {
        this.identifierPattern = Pattern.compile(identifierPattern);
    }

    public boolean accepts(String identifier) {
        return identifier != null
                && identifierPattern.matcher(identifier).matches();
    }
}
