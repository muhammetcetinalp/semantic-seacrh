package com.example.semantic_search.search;

import java.util.List;

/**
 * Reranks a list of search results by computing query-document relevance
 * with a cross-encoder model (second-stage ranking).
 */
public interface RerankingService {

    /**
     * Reranks the given results using the provided query.
     *
     * @param query   the original user query
     * @param results candidate results from first-stage retrieval
     * @param topN    how many results to return after reranking
     * @return reranked results, best first, limited to topN
     */
    List<RerankedResult> rerank(String query, List<HybridExplainResponse.FusionResult> results, int topN);

    /** Whether this service is currently available. */
    boolean isAvailable();

    record RerankedResult(int originalIndex, double relevanceScore, HybridExplainResponse.FusionResult result) {}
}
