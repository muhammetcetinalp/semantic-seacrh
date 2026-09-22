package com.example.semantic_search.embedding;

/**
 * Mock vektör sağlayıcı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.embedding.MockEmbeddingProvider} paketine taşınmıştır.
 */
@Deprecated
public class MockEmbeddingProvider extends com.example.semantic_search.client.embedding.MockEmbeddingProvider {

    public MockEmbeddingProvider(com.example.semantic_search.config.EmbeddingProperties properties) {
        super(properties);
    }
}
