package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;

public record RobotsTxtEntry(
        String identifier,
        ReferencementPageType pageType
) {
}
