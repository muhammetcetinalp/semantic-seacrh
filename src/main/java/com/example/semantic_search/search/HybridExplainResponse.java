package com.example.semantic_search.search;

import java.util.List;
import java.util.Map;

public record HybridExplainResponse(
        String query,
        String indexName,
        long tookMs,
        HybridSettings settings,
        SearchStage bm25,
        SearchStage semantic,
        List<FusionResult> finalResults,
        int totalCandidates,
        /** Null when reranking is disabled. Present when reranking ran successfully or fell back. */
        RerankStage rerank) {

    /** Convenience constructor — no reranking. */
    public HybridExplainResponse(String query, String indexName, long tookMs,
                                  HybridSettings settings, SearchStage bm25, SearchStage semantic,
                                  List<FusionResult> finalResults, int totalCandidates) {
        this(query, indexName, tookMs, settings, bm25, semantic, finalResults, totalCandidates, null);
    }

    public record HybridSettings(
            int limit,
            int candidateLimit,
            int candidateMultiplier,
            int rankConstant,
            double bm25Weight,
            double semanticWeight,
            List<String> types,
            Map<String, Object> filters) { }

    public record SearchStage(String method, long tookMs, List<RankedResult> results) { }

    public record TokenMatch(String queryToken, String matchedDocToken, double similarity) { }

    public record RankedResult(int rank, double originalScore, SearchResult document, List<TokenMatch> tokenMatches) {
        public RankedResult(int rank, double originalScore, SearchResult document) {
            this(rank, originalScore, document, List.of());
        }
    }

    public record FusionResult(
            int finalRank,
            double rrfScore,
            Integer bm25Rank,
            Integer semanticRank,
            double bm25Contribution,
            double semanticContribution,
            SearchResult document) { }

    public record RerankStage(
            String model,
            long tookMs,
            boolean usedFallback,
            List<RerankedFusionResult> results) { }

    public record RerankedFusionResult(
            int finalRank,
            double relevanceScore,
            double rrfScore,
            Integer bm25Rank,
            Integer semanticRank,
            SearchResult document) { }
}
