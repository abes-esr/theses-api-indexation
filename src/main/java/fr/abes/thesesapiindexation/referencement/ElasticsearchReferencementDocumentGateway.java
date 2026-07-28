package fr.abes.thesesapiindexation.referencement;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import org.elasticsearch.client.ResponseException;

import java.io.IOException;
import java.util.Optional;

public class ElasticsearchReferencementDocumentGateway
        implements ReferencementDocumentGateway {

    private static final String VERSION_CONFLICT =
            "version_conflict_engine_exception";
    private static final int HTTP_CONFLICT = 409;

    private final ElasticsearchClient client;
    private final String indexName;

    public ElasticsearchReferencementDocumentGateway(
            ElasticsearchClient client,
            String indexName
    ) {
        this.client = client;
        this.indexName = indexName;
    }

    @Override
    public Optional<ReferencementDocument> findById(String id) {
        try {
            var response = client.get(
                    request -> request.index(indexName).id(id),
                    ReferencementDocument.class
            );
            return response.found()
                    ? Optional.ofNullable(response.source())
                    : Optional.empty();
        } catch (ElasticsearchException | IOException exception) {
            throw accessFailure("lire", id, exception);
        }
    }

    @Override
    public void save(String id, ReferencementDocument document) {
        try {
            client.index(request -> request
                    .index(indexName)
                    .id(id)
                    .document(document));
        } catch (ElasticsearchException | IOException exception) {
            throw accessFailure("écrire", id, exception);
        }
    }

    @Override
    public boolean createIfAbsent(
            String id,
            ReferencementDocument document
    ) {
        try {
            client.index(request -> request
                    .index(indexName)
                    .id(id)
                    .opType(OpType.Create)
                    .document(document));
            return true;
        } catch (ElasticsearchException exception) {
            if (VERSION_CONFLICT.equals(exception.error().type())) {
                return false;
            }
            throw accessFailure("créer", id, exception);
        } catch (IOException exception) {
            if (isVersionConflict(exception)) {
                return false;
            }
            throw accessFailure("créer", id, exception);
        }
    }

    private boolean isVersionConflict(IOException exception) {
        return exception instanceof ResponseException responseException
                && responseException.getResponse()
                .getStatusLine()
                .getStatusCode() == HTTP_CONFLICT;
    }

    private ReferencementDocumentAccessException accessFailure(
            String operation,
            String id,
            Exception cause
    ) {
        return new ReferencementDocumentAccessException(
                "Impossible de " + operation
                        + " le référencement " + id
                        + " dans l’index " + indexName,
                cause
        );
    }
}
