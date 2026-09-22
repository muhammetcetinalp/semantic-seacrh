package com.example.semantic_search.embedding;

import org.springframework.web.client.RestClient;

/**
 * REST tabanlı vektör sağlayıcı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.embedding.RestEmbeddingProvider} paketine taşınmıştır.
 */
@Deprecated
public class RestEmbeddingProvider extends com.example.semantic_search.client.embedding.RestEmbeddingProvider {

    public RestEmbeddingProvider(RestClient.Builder restClientBuilder, com.example.semantic_search.config.EmbeddingProperties properties) {
        super(restClientBuilder, properties);
    }
}
