package fr.abes.thesesapiindexation.referencement;

import java.io.InputStream;

public interface ReferencementIndexGateway {

    boolean exists(String indexName);

    void create(String indexName, InputStream mapping);

    InputStream mapping(String indexName);
}
