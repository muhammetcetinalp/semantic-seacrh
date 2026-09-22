package com.example.semantic_search.opensearch;

/**
 * OpenSearch sağlık göstergesi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.client.opensearch.OpenSearchHealthIndicator} paketine taşınmıştır.
 */
@Deprecated
public class OpenSearchHealthIndicator extends com.example.semantic_search.client.opensearch.OpenSearchHealthIndicator {

    public OpenSearchHealthIndicator(com.example.semantic_search.client.opensearch.OpenSearchAdapter adapter) {
        super(adapter);
    }
}
