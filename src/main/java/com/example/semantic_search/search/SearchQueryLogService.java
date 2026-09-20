package com.example.semantic_search.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Asynchronously persists every hybrid explain request and standard search to Oracle for debugging.
 * Fire-and-forget: runs in a worker thread, failure is only logged.
 */
@Service
public class SearchQueryLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryLogService.class);

    private final SearchQueryLogRepository repository;
    private final ObjectMapper objectMapper;

    public SearchQueryLogService(SearchQueryLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Called after a standard /api/v1/search request — persists the query and results to Oracle.
     * Fire-and-forget: runs in a worker thread, failure is only logged.
     */
    @Async
    public void logStandardSearch(SearchRequest request, SearchResponse response, String indexName) {
        try {
            SearchQueryLog entry = new SearchQueryLog();
            entry.setQuery(request.getQuery());
            entry.setIndexName(indexName != null ? indexName : (request.getIndexName() != null ? request.getIndexName() : "unknown"));
            entry.setSearchType(request.getSearchType() != null ? request.getSearchType().name() : "STANDARD");
            entry.setStatus("SUCCESS");

            entry.setTookMs(response.getTookMs());
            entry.setResultLimit(request.getLimit());
            entry.setFinalResultCount(response.getResults() != null ? response.getResults().size() : 0);
            entry.setTotalCandidates((int) Math.min(response.getTotalHits(), Integer.MAX_VALUE));

            if (response.getResults() != null) {
                entry.setFinalResultsJson(toJson(response.getResults()));
            }

            Map<String, Object> settings = new HashMap<>();
            settings.put("searchType", entry.getSearchType());
            settings.put("limit", request.getLimit() != null ? request.getLimit() : 10);
            settings.put("offset", request.getOffset() != null ? request.getOffset() : 0);
            if (request.getFilters() != null) {
                settings.put("filters", request.getFilters());
            }
            if (request.getTypes() != null) {
                settings.put("types", request.getTypes());
            }
            entry.setSettingsJson(toJson(settings));

            repository.save(entry);
            log.debug("Standard search log saved — query='{}', id={}", request.getQuery(), entry.getId());
        } catch (Exception e) {
            log.warn("Failed to persist standard search query log for query='{}': {}", request.getQuery(), e.getMessage());
        }
    }

    /**
     * Called when standard /api/v1/search throws an exception — logs the error for diagnostics.
     */
    @Async
    public void logStandardError(SearchRequest request, String indexName, Throwable error) {
        try {
            SearchQueryLog entry = new SearchQueryLog();
            entry.setQuery(request.getQuery());
            entry.setIndexName(indexName != null ? indexName : (request.getIndexName() != null ? request.getIndexName() : "unknown"));
            entry.setSearchType(request.getSearchType() != null ? request.getSearchType().name() : "STANDARD");
            entry.setStatus("ERROR");
            entry.setErrorMessage(error.getClass().getSimpleName() + ": " + error.getMessage());
            if (request.getLimit() != null) {
                entry.setResultLimit(request.getLimit());
            }

            repository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist standard search error log: {}", e.getMessage());
        }
    }

    /**
     * Called after a successful explainHybrid — persists the full result to Oracle.
     * Fire-and-forget: runs in a worker thread, failure is only logged.
     */
    @Async
    public void logSuccess(HybridExplainRequest request, HybridExplainResponse response) {
        try {
            SearchQueryLog entry = new SearchQueryLog();
            entry.setQuery(request.getQuery());
            entry.setIndexName(response.indexName());
            entry.setSearchType("HYBRID_EXPLAIN");
            entry.setStatus("SUCCESS");

            // Timing
            entry.setTookMs(response.tookMs());
            entry.setBm25TookMs(response.bm25().tookMs());
            entry.setSemanticTookMs(response.semantic().tookMs());

            // Settings
            HybridExplainResponse.HybridSettings s = response.settings();
            entry.setBm25Weight(BigDecimal.valueOf(s.bm25Weight()));
            entry.setSemanticWeight(BigDecimal.valueOf(s.semanticWeight()));
            entry.setRankConstant(s.rankConstant());
            entry.setCandidateLimit(s.candidateLimit());
            entry.setResultLimit(s.limit());

            // Counts
            entry.setBm25ResultCount(response.bm25().results().size());
            entry.setSemanticResultCount(response.semantic().results().size());
            entry.setFinalResultCount(response.finalResults().size());
            entry.setTotalCandidates(response.totalCandidates());

            // Full JSON payloads
            entry.setBm25ResultsJson(toJson(response.bm25().results()));
            entry.setSemanticResultsJson(toJson(response.semantic().results()));
            entry.setFinalResultsJson(toJson(response.finalResults()));
            entry.setSettingsJson(toJson(s));

            repository.save(entry);
            log.debug("Search log saved — query='{}', id={}", request.getQuery(), entry.getId());

        } catch (Exception e) {
            log.warn("Failed to persist search query log for query='{}': {}", request.getQuery(), e.getMessage());
        }
    }

    /**
     * Called when explainHybrid throws an exception — logs the error for diagnostics.
     */
    @Async
    public void logError(HybridExplainRequest request, String indexName, Throwable error) {
        try {
            SearchQueryLog entry = new SearchQueryLog();
            entry.setQuery(request.getQuery());
            entry.setIndexName(indexName != null ? indexName : "unknown");
            entry.setSearchType("HYBRID_EXPLAIN");
            entry.setStatus("ERROR");
            entry.setErrorMessage(error.getClass().getSimpleName() + ": " + error.getMessage());

            // Preserve settings even on error
            if (request.getBm25Weight() != null)
                entry.setBm25Weight(BigDecimal.valueOf(request.getBm25Weight()));
            if (request.getSemanticWeight() != null)
                entry.setSemanticWeight(BigDecimal.valueOf(request.getSemanticWeight()));
            if (request.getRankConstant() != null)   entry.setRankConstant(request.getRankConstant());
            if (request.getLimit() != null)          entry.setResultLimit(request.getLimit());

            repository.save(entry);

        } catch (Exception e) {
            log.warn("Failed to persist error search log: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
