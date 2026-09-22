package com.example.semantic_search.embedding;

import org.springframework.web.client.RestClient;

/**
 * TEI tabanlı vektör sağlayıcı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.embedding.TeiEmbeddingProvider} paketine taşınmıştır.
 */
@Deprecated
public class TeiEmbeddingProvider extends com.example.semantic_search.client.embedding.TeiEmbeddingProvider {

    public TeiEmbeddingProvider(RestClient.Builder restClientBuilder, com.example.semantic_search.config.EmbeddingProperties properties) {
        super(restClientBuilder, properties);
    }
}
