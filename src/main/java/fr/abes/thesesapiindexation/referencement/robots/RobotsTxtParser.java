package fr.abes.thesesapiindexation.referencement.robots;

import fr.abes.thesesapiindexation.referencement.ReferencementPageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class RobotsTxtParser {

    private static final Pattern ROOT_DISALLOW = Pattern.compile(
            "^\\s*Disallow\\s*:\\s*/([^/\\s]+)\\s*$"
    );
    private static final Pattern IDENTIFIER_CANDIDATE = Pattern.compile(
            "(?:[0-9]{4}.*|[0-9]{8,}.*|s[0-9]+.*)"
    );

    public RobotsTxtParseResult parse(String content) {
        Objects.requireNonNull(content, "content");
        var lines = content.lines().toList();
        Map<String, RobotsTxtEntry> entries = new LinkedHashMap<>();
        int duplicates = 0;
        int ignored = 0;
        int invalid = 0;

        for (String line : lines) {
            var matcher = ROOT_DISALLOW.matcher(line);
            if (!matcher.matches()) {
                ignored++;
                continue;
            }

            String identifier = matcher.group(1);
            if (identifier.contains(".")) {
                ignored++;
                continue;
            }

            var pageType =
                    ReferencementPageType.fromIdentifier(identifier);
            if (pageType.isPresent()) {
                RobotsTxtEntry previous = entries.putIfAbsent(
                        identifier,
                        new RobotsTxtEntry(
                                identifier,
                                pageType.orElseThrow()
                        )
                );
                if (previous != null) {
                    duplicates++;
                }
            } else if (IDENTIFIER_CANDIDATE
                    .matcher(identifier)
                    .matches()) {
                invalid++;
            } else {
                ignored++;
            }
        }

        return new RobotsTxtParseResult(
                entries.values().stream().toList(),
                lines.size(),
                duplicates,
                ignored,
                invalid
        );
    }
}
