package com.example.semantic_search.search;

/**
 * @deprecated Heuristic / rule-based reranking logic has been completely removed.
 * Real neural Cross-Encoder reranking is provided by {@link TeiRerankingService}
 * backed by Hugging Face TEI (e.g. BAAI/bge-reranker-v2-m3).
 */
@Deprecated
public class NativeJavaRerankingService {
    // All simulated / rule-based heuristics have been nuked.
    // Use TeiRerankingService for actual cross-encoder model evaluation.
}
