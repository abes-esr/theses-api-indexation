package fr.abes.thesesapiindexation.referencement;

import org.springframework.core.io.Resource;

public record ReferencementIndexProperties(String name, Resource mapping) {
}
