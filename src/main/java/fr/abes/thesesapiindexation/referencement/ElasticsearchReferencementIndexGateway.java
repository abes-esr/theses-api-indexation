package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpMapper;
import jakarta.json.stream.JsonGenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class ElasticsearchReferencementIndexGateway implements ReferencementIndexGateway {

    private static final String INDEX_ALREADY_EXISTS = "resource_already_exists_exception";

    private final ElasticsearchClient client;
    private final JsonpMapper mapper;

    public ElasticsearchReferencementIndexGateway(
            ElasticsearchClient client,
            JsonpMapper mapper
    ) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public boolean exists(String indexName) {
        try {
            return client.indices()
                    .exists(request -> request.index(indexName))
                    .value();
        } catch (ElasticsearchException | IOException exception) {
            throw new IllegalStateException(
                    "Impossible de contrôler l'existence de l'index " + indexName,
                    exception
            );
        }
    }

    @Override
    public void create(String indexName, InputStream mapping) {
        try {
            boolean acknowledged = client.indices()
                    .create(request -> request.index(indexName).withJson(mapping))
                    .acknowledged();
            if (!acknowledged) {
                throw new IllegalStateException(
                        "La création de l'index " + indexName + " n'a pas été acquittée"
                );
            }
        } catch (ElasticsearchException exception) {
            if (INDEX_ALREADY_EXISTS.equals(exception.error().type())) {
                throw new ReferencementIndexAlreadyExistsException(indexName, exception);
            }
            throw new IllegalStateException(
                    "Impossible de créer l'index " + indexName,
                    exception
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Impossible de créer l'index " + indexName,
                    exception
            );
        }
    }

    @Override
    public InputStream mapping(String indexName) {
        try {
            GetMappingResponse response = client.indices()
                    .getMapping(request -> request.index(indexName));
            IndexMappingRecord indexMapping = response.result().get(indexName);
            if (indexMapping == null) {
                throw new IllegalStateException(
                        "Elasticsearch n'a retourné aucun mapping pour l'index " + indexName
                );
            }

            StringWriter writer = new StringWriter();
            try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
                generator.writeStartObject();
                generator.writeKey("mappings");
                indexMapping.mappings().serialize(generator, mapper);
                generator.writeEnd();
            }
            return new ByteArrayInputStream(
                    writer.toString().getBytes(StandardCharsets.UTF_8)
            );
        } catch (ElasticsearchException | IOException exception) {
            throw new IllegalStateException(
                    "Impossible de lire le mapping de l'index " + indexName,
                    exception
            );
        }
    }
}
