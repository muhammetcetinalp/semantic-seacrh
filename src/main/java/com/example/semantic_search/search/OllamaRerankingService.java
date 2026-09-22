package com.example.semantic_search.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reranking via Ollama's /v1/rerank endpoint (Cohere-compatible format).
 * Activate with: search.reranking.enabled=true
 *
 * <p>Uses the cross-encoder model to compute query-document relevance scores
 * for a set of candidate results, replacing first-stage retrieval scores.
 */
@Service
@ConditionalOnProperty(name = "search.reranking.provider", havingValue = "ollama")
public class OllamaRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaRerankingService.class);

    private final RestClient restClient;
    private final String model;

    public OllamaRerankingService(
            RestClient.Builder restClientBuilder,
            @Value("${search.reranking.endpoint:http://localhost:11434}") String endpoint,
            @Value("${search.reranking.model:BAAI/bge-reranker-v2-m3}") String model) {
        this.restClient = restClientBuilder.baseUrl(endpoint).build();
        this.model = model;
        log.info("Cross-Encoder reranking active — endpoint={}, model={}", endpoint, model);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RerankedResult> rerank(String query,
                                       List<HybridExplainResponse.FusionResult> results,
                                       int topN) {
        if (results.isEmpty()) return List.of();

        // Build document texts for the reranker (title + searchText)
        List<String> documents = results.stream()
                .map(r -> buildDocumentText(r.document()))
                .toList();

        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "query", query,
                    "documents", documents,
                    "top_n", Math.min(topN, results.size())
            );

            Map<String, Object> response = restClient.post()
                    .uri("/v1/rerank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("results")) {
                log.warn("Empty reranking response — falling back to original order");
                return fallback(results, topN);
            }

            List<Map<String, Object>> rerankResults = (List<Map<String, Object>>) response.get("results");
            List<RerankedResult> reranked = new ArrayList<>();

            for (Map<String, Object> item : rerankResults) {
                int index = ((Number) item.get("index")).intValue();
                double score = ((Number) item.get("relevance_score")).doubleValue();
                reranked.add(new RerankedResult(index, score, results.get(index)));
            }

            reranked.sort(Comparator.comparingDouble(RerankedResult::relevanceScore).reversed());
            log.debug("Reranked {} candidates → top {}", results.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("Cross-encoder reranking endpoint returned error ({}) — falling back to RRF ranking (Note: Ollama requires TEI or /v1/rerank proxy for cross-encoders)", e.getMessage());
            return fallback(results, topN);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            try {
                restClient.get().uri("/health").retrieve().toBodilessEntity();
                return true;
            } catch (Exception e1) {
                try {
                    restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
                    return true;
                } catch (Exception e2) {
                    restClient.get().uri("/").retrieve().toBodilessEntity();
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Fallback: wrap original results preserving RRF score as relevance. */
    private List<RerankedResult> fallback(List<HybridExplainResponse.FusionResult> results, int topN) {
        return results.stream()
                .limit(topN)
                .map(r -> new RerankedResult(results.indexOf(r), r.rrfScore(), r))
                .toList();
    }

    private String buildDocumentText(com.example.semantic_search.search.SearchResult doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.getTitle() != null) sb.append(doc.getTitle()).append(". ");
        if (doc.getSearchText() != null) sb.append(doc.getSearchText());
        return sb.toString().strip();
    }
}
