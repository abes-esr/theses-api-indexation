package fr.abes.thesesapiindexation.referencement.robots;

import java.util.List;

public record RobotsTxtParseResult(
        List<RobotsTxtEntry> entries,
        int totalLines,
        int duplicates,
        int ignored,
        int invalid
) {
    public RobotsTxtParseResult {
        entries = List.copyOf(entries);
    }

    public int valid() {
        return entries.size();
    }
}
