package com.example.semantic_search.service;

import com.example.semantic_search.dto.HybridExplainRequest;
import com.example.semantic_search.dto.HybridExplainResponse;
import com.example.semantic_search.dto.SearchRequest;
import com.example.semantic_search.dto.SearchResponse;
import com.example.semantic_search.model.SearchQueryLog;
import com.example.semantic_search.repository.SearchQueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Kullanıcıların gerçekleştirdiği standart arama ve hibrit açıklama (hybrid explain)
 * sorgularını, parametrelerini, çalışma sürelerini ve sonuçlarını asenkron olarak PostgreSQL
 * veritabanına kaydeden denetim (audit) ve loglama servisi.
 *
 * <p>{@code @Async} ile ayrı bir iş parçacığında çalıştırılarak (fire-and-forget)
 * ana arama isteğinin gecikme süresine (latency) etki etmez.</p>
 */
@Service
public class SearchQueryLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryLogService.class);

    private final SearchQueryLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Log servisi bileşenini yapılandıran yapıcı metot.
     *
     * @param repository Arama logu JPA veritabanı deposu
     * @param objectMapper JSON serileştirme dönüştürücüsü
     */
    public SearchQueryLogService(SearchQueryLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Standart /api/v1/search arama isteği başarıyla sonuçlandığında asenkron olarak çağrılır ve detayları kaydeder.
     *
     * @param request Arama isteği DTO'su
     * @param response Üretilen arama yanıtı
     * @param indexName Hedef indeks adı
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
            log.debug("Standart arama logu kaydedildi — sorgu='{}', id={}", request.getQuery(), entry.getId());
        } catch (Exception e) {
            log.warn("Standart arama logu kaydedilemedi ('{}'): {}", request.getQuery(), e.getMessage());
        }
    }

    /**
     * Standart arama sırasında bir istisna oluştuğunda hata detayını kaydeder.
     *
     * @param request Arama isteği
     * @param indexName İndeks adı
     * @param error Yakalanan hata istisnası
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
            log.warn("Standart arama hata logu kaydedilemedi: {}", e.getMessage());
        }
    }

    /**
     * Başarılı bir Hibrit Açıklama (/api/v1/search/explain) sonucunu tüm aşama süreleri ve JSON detaylarıyla kaydeder.
     *
     * @param request Açıklama isteği
     * @param response Üretilen hibrit açıklama yanıtı
     */
    @Async
    public void logSuccess(HybridExplainRequest request, HybridExplainResponse response) {
        try {
            SearchQueryLog entry = new SearchQueryLog();
            entry.setQuery(request.getQuery());
            entry.setIndexName(response.indexName());
            entry.setSearchType("HYBRID_EXPLAIN");
            entry.setStatus("SUCCESS");

            // Zamanlamalar
            entry.setTookMs(response.tookMs());
            entry.setBm25TookMs(response.bm25().tookMs());
            entry.setSemanticTookMs(response.semantic().tookMs());

            // Ayarlar
            HybridExplainResponse.HybridSettings s = response.settings();
            entry.setBm25Weight(BigDecimal.valueOf(s.bm25Weight()));
            entry.setSemanticWeight(BigDecimal.valueOf(s.semanticWeight()));
            entry.setRankConstant(s.rankConstant());
            entry.setCandidateLimit(s.candidateLimit());
            entry.setResultLimit(s.limit());

            // Sayılar
            entry.setBm25ResultCount(response.bm25().results().size());
            entry.setSemanticResultCount(response.semantic().results().size());
            entry.setFinalResultCount(response.finalResults().size());
            entry.setTotalCandidates(response.totalCandidates());

            // JSON Yükleri
            entry.setBm25ResultsJson(toJson(response.bm25().results()));
            entry.setSemanticResultsJson(toJson(response.semantic().results()));
            entry.setFinalResultsJson(toJson(response.finalResults()));
            entry.setSettingsJson(toJson(s));

            repository.save(entry);
            log.debug("Hibrit arama logu kaydedildi — sorgu='{}', id={}", request.getQuery(), entry.getId());

        } catch (Exception e) {
            log.warn("Hibrit arama logu kaydedilemedi ('{}'): {}", request.getQuery(), e.getMessage());
        }
    }

    /**
     * Hibrit açıklama araması sırasında hata fırlatıldığında hatayı ve parametreleri kaydeder.
     *
     * @param request Açıklama isteği
     * @param indexName İndeks adı
     * @param error Fırlatılan hata
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

            if (request.getBm25Weight() != null)
                entry.setBm25Weight(BigDecimal.valueOf(request.getBm25Weight()));
            if (request.getSemanticWeight() != null)
                entry.setSemanticWeight(BigDecimal.valueOf(request.getSemanticWeight()));
            if (request.getRankConstant() != null)   entry.setRankConstant(request.getRankConstant());
            if (request.getLimit() != null)          entry.setResultLimit(request.getLimit());

            repository.save(entry);

        } catch (Exception e) {
            log.warn("Hibrit hata logu kaydedilemedi: {}", e.getMessage());
        }
    }

    /**
     * Nesneyi güvenli şekilde JSON metnine serileştirir.
     *
     * @param obj Serileştirilecek nesne
     * @return JSON dizesi
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
