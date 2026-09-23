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

import java.util.*;

/**
 * Hugging Face Text Embeddings Inference (TEI) veya Infinity sunucuları arkasındaki
 * Cross-Encoder modelini (örneğin {@code BAAI/bge-reranker-v2-m3}) kullanan kurumsal seviye yeniden sıralama servisi.
 *
 * <p>Sorgu ile doküman metnini birleştirip ortak bir bağlam içerisinde derin çift yönlü dikkat
 * (bidirectional cross-attention) uygulayarak nihai bir anlamsal ilgi puanı üretir.</p>
 */
@Service
@ConditionalOnProperty(name = "search.reranking.provider", havingValue = "tei", matchIfMissing = true)
public class TeiRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(TeiRerankingService.class);

    private final RestClient restClient;
    private final String model;
    private final String endpoint;

    /**
     * TEI Reranking servisini yapılandıran yapıcı metot.
     *
     * @param restClientBuilder Spring RestClient yapıcısı
     * @param endpoint Model sunucusunun HTTP uç noktası adresi
     * @param model Kullanılacak Cross-Encoder modelinin adı/yolu
     */
    public TeiRerankingService(
            RestClient.Builder restClientBuilder,
            String endpoint,
            String model) {
        this(restClientBuilder, endpoint, model, null, "Authorization");
    }

    public TeiRerankingService(
            RestClient.Builder restClientBuilder,
            @Value("${search.reranking.endpoint:http://localhost:8082}") String endpoint,
            @Value("${search.reranking.model:BAAI/bge-reranker-v2-m3}") String model,
            @Value("${search.reranking.api-key:${search.ai.api-key:}}") String apiKey,
            @Value("${search.reranking.api-key-header:Authorization}") String apiKeyHeader) {
        this.endpoint = endpoint;
        this.model = model;
        RestClient.Builder builder = restClientBuilder.baseUrl(endpoint);
        if (apiKey != null && !apiKey.isBlank()) {
            String key = apiKey.trim();
            String header = apiKeyHeader != null ? apiKeyHeader.trim() : "Authorization";
            if ("Authorization".equalsIgnoreCase(header) && !key.toLowerCase().startsWith("bearer ")) {
                builder.defaultHeader("Authorization", "Bearer " + key);
            } else {
                builder.defaultHeader(header, key);
            }
            builder.defaultHeader("X-API-Key", key);
        }
        this.restClient = builder.build();
        log.info("Cross-Encoder Reranker aktif — provider=tei, endpoint={}, model={}, auth={}",
                endpoint, model, (apiKey != null && !apiKey.isBlank()) ? "API-Key aktif" : "yok");
    }

    /**
     * İlk aşama arama sonuçlarını TEI/Infinity {@code /rerank} uç noktasına göndererek yeniden puanlar ve sıralar.
     *
     * @param query Kullanıcı arama sorgusu
     * @param results İlk aşamadan gelen aday füzyon sonuçları
     * @param topN Dönecek en iyi sonuç sayısı
     * @return Model alaka puanına göre sıralanmış sonuçlar listesi
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<RerankedResult> rerank(String query,
                                       List<HybridExplainResponse.FusionResult> results,
                                       int topN) {
        if (results == null || results.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        long start = System.currentTimeMillis();

        // Cross-encoder için aday doküman metinlerini oluştur (başlık + arama metni)
        List<String> texts = results.stream()
                .map(r -> buildDocumentText(r.document()))
                .toList();

        try {
            Object responseObj = null;
            try {
                // Birincil format: Infinity / Cohere API ("documents" ve isteğe bağlı "model")
                Map<String, Object> infinityBody = new HashMap<>();
                infinityBody.put("query", query);
                infinityBody.put("documents", texts);
                if (model != null && !model.isBlank()) {
                    infinityBody.put("model", model);
                }
                responseObj = restClient.post()
                        .uri("/rerank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(infinityBody)
                        .retrieve()
                        .body(Object.class);
            } catch (Exception ex) {
                log.debug("Standart Infinity /rerank formatı başarısız: {}. TEI formatı deneniyor...", ex.getMessage());
                // Yedek format: Hugging Face TEI API ("texts" ve "truncate")
                Map<String, Object> teiBody = Map.of(
                        "query", query,
                        "texts", texts,
                        "truncate", true
                );
                responseObj = restClient.post()
                        .uri("/rerank")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(teiBody)
                        .retrieve()
                        .body(Object.class);
            }

            List<RerankedResult> reranked = new ArrayList<>();

            if (responseObj instanceof List<?> list) {
                // Hugging Face TEI formatı: [{"index": 0, "score": 0.985}, ...]
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        int index = ((Number) map.get("index")).intValue();
                        Number scoreNum = map.containsKey("score")
                                ? (Number) map.get("score")
                                : (Number) map.get("relevance_score");
                        double score = scoreNum != null ? scoreNum.doubleValue() : 0.0;
                        score = Math.round(score * 10000.0) / 10000.0;
                        if (index >= 0 && index < results.size()) {
                            reranked.add(new RerankedResult(index, score, results.get(index)));
                        }
                    }
                }
            } else if (responseObj instanceof Map<?, ?> map) {
                // Infinity / Cohere formatı: {"results": [{"index": 0, "relevance_score": 0.985}, ...]}
                List<?> items = null;
                if (map.get("results") instanceof List<?> rList) {
                    items = rList;
                } else if (map.get("data") instanceof List<?> dList) {
                    items = dList;
                }
                if (items != null) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> resMap) {
                            int index = ((Number) resMap.get("index")).intValue();
                            Number scoreNum = resMap.containsKey("relevance_score")
                                    ? (Number) resMap.get("relevance_score")
                                    : (Number) resMap.get("score");
                            double score = scoreNum != null ? scoreNum.doubleValue() : 0.0;
                            score = Math.round(score * 10000.0) / 10000.0;
                            if (index >= 0 && index < results.size()) {
                                reranked.add(new RerankedResult(index, score, results.get(index)));
                            }
                        }
                    }
                }
            }

            if (reranked.isEmpty()) {
                log.warn("Yeniden sıralayıcıdan beklenmeyen yanıt yapısı: {} — füzyon sıralamasına dönülüyor", responseObj);
                return fallback(results, topN);
            }

            // Model alaka puanına göre azalan sırada sırala
            reranked.sort(Comparator.comparingDouble(RerankedResult::relevanceScore).reversed());

            int limit = Math.min(topN > 0 ? topN : reranked.size(), reranked.size());
            List<RerankedResult> topResults = reranked.subList(0, limit);

            long tookMs = System.currentTimeMillis() - start;
            log.info("Cross-Encoder {} adayı {}ms sürede yeniden sıraladı (en yüksek skor={})",
                    results.size(), tookMs, (!topResults.isEmpty() ? topResults.get(0).relevanceScore() : 0));

            return topResults;

        } catch (Exception e) {
            log.error("Cross-Encoder yeniden sıralama uç noktası ({}) hatası: {} — RRF füzyon puanına dönülüyor",
                    endpoint, e.getMessage(), e);
            return fallback(results, topN);
        }
    }

    /**
     * TEI / Infinity sunucusunun /health veya /ready uç noktasına istek atarak servisin canlılığını kontrol eder.
     *
     * @return Sunucu ayaktaysa true, aksi halde false
     */
    @Override
    public boolean isAvailable() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            try {
                restClient.get().uri("/ready").retrieve().toBodilessEntity();
                return true;
            } catch (Exception e2) {
                try {
                    restClient.get().uri("/docs").retrieve().toBodilessEntity();
                    return true;
                } catch (Exception ex) {
                    log.debug("Reranker sağlık kontrolü başarısız {}: {}", endpoint, ex.getMessage());
                    return false;
                }
            }
        }
    }

    /**
     * Model sunucusuna ulaşılamadığında veya hata aldığında orijinal ilk aşama sıralamasını koruyan geri düşüş metodu.
     *
     * @param results Orijinal sonuçlar
     * @param topN İstenen sonuç sayısı
     * @return Geri düşüş sonuç listesi
     */
    private List<RerankedResult> fallback(List<HybridExplainResponse.FusionResult> results, int topN) {
        int limit = Math.min(topN > 0 ? topN : results.size(), results.size());
        return results.stream()
                .limit(limit)
                .map(r -> new RerankedResult(results.indexOf(r), r.rrfScore(), r))
                .toList();
    }

    /**
     * Dokümanın başlık ve arama metnini Cross-Encoder'ın değerlendirebileceği temiz bir metin bloğuna dönüştürür.
     *
     * @param doc Arama sonucu dokümanı
     * @return Birleşik doküman metni
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
