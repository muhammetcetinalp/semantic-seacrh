package com.example.semantic_search.qdrant;

import org.springframework.web.client.RestClient;

/**
 * Qdrant istemci adaptörü.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.qdrant.QdrantAdapter} paketine taşınmıştır.
 */
@Deprecated
public class QdrantAdapter extends com.example.semantic_search.client.qdrant.QdrantAdapter {

    public QdrantAdapter(RestClient.Builder restClientBuilder, com.example.semantic_search.config.QdrantProperties properties) {
        super(restClientBuilder, properties);
    }
}
