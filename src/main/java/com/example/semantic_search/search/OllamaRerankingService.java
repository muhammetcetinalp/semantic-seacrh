package com.example.semantic_search.search;

import org.springframework.web.client.RestClient;

/**
 * Ollama tabanlı yeniden sıralayıcı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.OllamaRerankingService} paketine taşınmıştır.
 */
@Deprecated
public class OllamaRerankingService extends com.example.semantic_search.service.OllamaRerankingService {

    public OllamaRerankingService(RestClient.Builder restClientBuilder, String endpoint, String model) {
        super(restClientBuilder, endpoint, model);
    }
}
