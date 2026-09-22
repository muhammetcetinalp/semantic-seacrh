package com.example.semantic_search.search;

/**
 * Arama sorgu logu servisi.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.service.SearchQueryLogService} paketine taşınmıştır.
 */
@Deprecated
public class SearchQueryLogService extends com.example.semantic_search.service.SearchQueryLogService {

    public SearchQueryLogService(com.example.semantic_search.repository.SearchQueryLogRepository repository,
                                 tools.jackson.databind.ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }
}
