package com.example.semantic_search.search;

import com.example.semantic_search.dto.SearchResult;
import com.example.semantic_search.model.SearchType;

import java.util.List;

/**
 * Arama yanıtı DTO'su.
 *
 * @deprecated Bu sınıf {@link com.example.semantic_search.dto.SearchResponse} paketine taşınmıştır.
 */
@Deprecated
public class SearchResponse extends com.example.semantic_search.dto.SearchResponse {

    public SearchResponse() {
        super();
    }

    public SearchResponse(List<SearchResult> results, long totalHits, long tookMs, SearchType searchType) {
        super(results, totalHits, tookMs, searchType);
    }
}
