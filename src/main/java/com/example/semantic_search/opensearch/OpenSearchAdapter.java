package com.example.semantic_search.opensearch;

/**
 * OpenSearch istemci adaptörü.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.opensearch.OpenSearchAdapter} paketine taşınmıştır.
 */
@Deprecated
public class OpenSearchAdapter extends com.example.semantic_search.client.opensearch.OpenSearchAdapter {

    public OpenSearchAdapter(org.opensearch.client.opensearch.OpenSearchClient client,
                             com.example.semantic_search.config.EmbeddingProperties embeddingProperties,
                             com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper documentSourceMapper,
                             tools.jackson.databind.ObjectMapper objectMapper) {
        super(client, embeddingProperties, documentSourceMapper, objectMapper);
    }
}
