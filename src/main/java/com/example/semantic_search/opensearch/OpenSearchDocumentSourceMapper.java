package com.example.semantic_search.opensearch;

/**
 * Açık arama doküman kaynak dönüştürücüsü.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper} paketine taşınmıştır.
 */
@Deprecated
public class OpenSearchDocumentSourceMapper extends com.example.semantic_search.client.opensearch.OpenSearchDocumentSourceMapper {

    public OpenSearchDocumentSourceMapper(tools.jackson.databind.ObjectMapper objectMapper) {
        super(objectMapper);
    }
}
