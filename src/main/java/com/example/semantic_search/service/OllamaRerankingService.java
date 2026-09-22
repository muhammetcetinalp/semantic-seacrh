package com.example.semantic_search.service;

import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchResult;
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
 * Ollama veya Ollama arkasındaki uyumlu /v1/rerank proxy uç noktası üzerinden
 * Cross-Encoder yeniden sıralama işlemlerini yürüten servis implementasyonu.
 *
 * <p>{@code search.reranking.provider=ollama} konfigürasyonu etkinleştirildiğinde devreye girer.</p>
 */
@Service
@ConditionalOnProperty(name = "search.reranking.provider", havingValue = "ollama")
public class OllamaRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaRerankingService.class);

    private final RestClient restClient;
    private final String model;

    /**
     * Ollama yeniden sıralama servisini yapılandıran yapıcı metot.
     *
     * @param restClientBuilder RestClient yapıcısı
     * @param endpoint Ollama sunucusu HTTP adresi
     * @param model Kullanılacak yeniden sıralama modeli
     */
    public OllamaRerankingService(
            RestClient.Builder restClientBuilder,
            @Value("${search.reranking.endpoint:http://localhost:11434}") String endpoint,
            @Value("${search.reranking.model:BAAI/bge-reranker-v2-m3}") String model) {
        this.restClient = restClientBuilder.baseUrl(endpoint).build();
        this.model = model;
        log.info("Cross-Encoder reranking aktif (Ollama) — endpoint={}, model={}", endpoint, model);
    }

    /**
     * Aday sonuçları Ollama /v1/rerank servisine ileterek yeniden puanlar.
     *
     * @param query Arama sorgusu
     * @param results İlk aşama sonuçları
     * @param topN Dönecek sonuç adedi
     * @return Yeniden sıralanmış liste
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<RerankedResult> rerank(String query,
                                       List<HybridExplainResponse.FusionResult> results,
                                       int topN) {
        if (results.isEmpty()) return List.of();

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
                log.warn("Ollama'dan boş yanıt — orijinal sıralamaya dönülüyor");
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
            log.debug("Ollama ile {} aday yeniden sıralandı", reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("Ollama /v1/rerank hatası ({}) — RRF sıralamasına dönülüyor", e.getMessage());
            return fallback(results, topN);
        }
    }

    /**
     * Ollama sunucusunun canlılığını kontrol eder.
     *
     * @return Sunucu erişilebilir ise true, değilse false
     */
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
                } catch (Exception ex) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Hata durumunda ilk aşama sıralamasını korur.
     *
     * @param results Orijinal sonuçlar
     * @param topN İstenen sonuç adedi
     * @return Geri düşüş sıralama listesi
     */
    private List<RerankedResult> fallback(List<HybridExplainResponse.FusionResult> results, int topN) {
        int limit = Math.min(topN > 0 ? topN : results.size(), results.size());
        return results.stream()
                .limit(limit)
                .map(r -> new RerankedResult(results.indexOf(r), r.rrfScore(), r))
                .toList();
    }

    /**
     * Doküman metnini birleştirir.
     *
     * @param doc Arama sonucu
     * @return Birleşik metin
     */
    private String buildDocumentText(SearchResult doc) {
        if (doc == null) return "olay";
        StringBuilder sb = new StringBuilder();
        if (doc.getTitle() != null && !doc.getTitle().isBlank()) {
            sb.append(doc.getTitle().strip()).append(". ");
        }
        if (doc.getSearchText() != null && !doc.getSearchText().isBlank()) {
            sb.append(doc.getSearchText().strip());
        }
        String text = sb.toString().strip();
        return text.isBlank() ? (doc.getId() != null ? doc.getId() : "olay") : text;
    }
}
