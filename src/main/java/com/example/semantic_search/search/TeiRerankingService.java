package com.example.semantic_search.search;

import org.springframework.web.client.RestClient;

/**
 * TEI tabanlı yeniden sıralayıcı.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.TeiRerankingService} paketine taşınmıştır.
 */
@Deprecated
public class TeiRerankingService extends com.example.semantic_search.service.TeiRerankingService {

    public TeiRerankingService(RestClient.Builder restClientBuilder, String endpoint, String model) {
        super(restClientBuilder, endpoint, model);
    }
}
