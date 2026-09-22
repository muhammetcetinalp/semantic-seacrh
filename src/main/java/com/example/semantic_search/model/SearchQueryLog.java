package com.example.semantic_search.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kullanıcıların çalıştırdığı her hibrit arama sorgusunu ve sonuç analizini denetim (audit)
 * ve optimizasyon amacıyla ilişkisel veritabanında saklayan JPA varlığı (Entity).
 *
 * <p>BM25, Dense Semantik ve RRF nihai sonuçları JSON metinleri olarak saklanarak, geçmiş aramaların
 * doğruluğu, alaka metrikleri ve sistem başarım süreleri geriye dönük incelenebilir.</p>
 */
@Entity
@Table(name = "search_query_log")
public class SearchQueryLog {

    /** Otomatik artan kayıt kimliği. */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "search_query_log_id")
    @SequenceGenerator(name = "search_query_log_id", sequenceName = "search_query_log_seq", allocationSize = 1)
    private Long id;

    // ── İstek Bağlamı ────────────────────────────────────────────────────────

    /** Kullanıcının girdiği arama sorgusu. */
    @Column(nullable = false, length = 2000)
    private String query;

    /** Sorgulanan OpenSearch indeksinin adı. */
    @Column(name = "index_name", nullable = false, length = 255)
    private String indexName;

    /** Arama operasyonu türü (örn: HYBRID_EXPLAIN). */
    @Column(name = "search_type", nullable = false, length = 50)
    private String searchType = "HYBRID_EXPLAIN";

    // ── Zamanlama Metrikleri ────────────────────────────────────────────────

    /** Toplam arama süresi (ms). */
    @Column(name = "took_ms")
    private Long tookMs;

    /** BM25 aramasının sürdüğü süre (ms). */
    @Column(name = "bm25_took_ms")
    private Long bm25TookMs;

    /** Semantik vektör aramasının sürdüğü süre (ms). */
    @Column(name = "semantic_took_ms")
    private Long semanticTookMs;

    // ── Parametreler ve Ağırlıklar ──────────────────────────────────────────

    /** Kullanılan BM25 katsayı ağırlığı. */
    @Column(name = "bm25_weight")
    private BigDecimal bm25Weight;

    /** Kullanılan Semantik katsayı ağırlığı. */
    @Column(name = "semantic_weight")
    private BigDecimal semanticWeight;

    /** Kullanılan RRF k sabiti. */
    @Column(name = "rank_constant")
    private Integer rankConstant;

    /** Her koldan çekilen aday tavan sayısı. */
    @Column(name = "candidate_limit")
    private Integer candidateLimit;

    /** İstenen sonuç limiti. */
    @Column(name = "result_limit")
    private Integer resultLimit;

    // ── Sonuç Sayıları ──────────────────────────────────────────────────────

    /** BM25 aşamasında bulunan aday adedi. */
    @Column(name = "bm25_result_count")
    private Integer bm25ResultCount;

    /** Semantik aşamada bulunan aday adedi. */
    @Column(name = "semantic_result_count")
    private Integer semanticResultCount;

    /** Nihai birleşimde dönen sonuç adedi. */
    @Column(name = "final_result_count")
    private Integer finalResultCount;

    /** Toplanan tekil aday sayısı. */
    @Column(name = "total_candidates")
    private Integer totalCandidates;

    // ── JSON Yükleri ────────────────────────────────────────────────────────

    /** BM25 sonuçlarının tam JSON dökümü. */
    @Column(name = "bm25_results_json", columnDefinition = "TEXT")
    private String bm25ResultsJson;

    /** Semantik arama sonuçlarının tam JSON dökümü. */
    @Column(name = "semantic_results_json", columnDefinition = "TEXT")
    private String semanticResultsJson;

    /** RRF birleşim sonuçlarının tam JSON dökümü. */
    @Column(name = "final_results_json", columnDefinition = "TEXT")
    private String finalResultsJson;

    /** İstek parametrelerinin JSON dökümü. */
    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;

    // ── Durum ve Hata ───────────────────────────────────────────────────────

    /** Hata meydana geldiyse hata mesajı. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** İşlem sonucu durumu (SUCCESS veya ERROR). */
    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    /** Log kaydının veritabanına yazılma zamanı. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Varlık kaydedilmeden önce oluşturulma zamanını atar.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public Long getTookMs() { return tookMs; }
    public void setTookMs(Long tookMs) { this.tookMs = tookMs; }

    public Long getBm25TookMs() { return bm25TookMs; }
    public void setBm25TookMs(Long bm25TookMs) { this.bm25TookMs = bm25TookMs; }

    public Long getSemanticTookMs() { return semanticTookMs; }
    public void setSemanticTookMs(Long semanticTookMs) { this.semanticTookMs = semanticTookMs; }

    public BigDecimal getBm25Weight() { return bm25Weight; }
    public void setBm25Weight(BigDecimal bm25Weight) { this.bm25Weight = bm25Weight; }

    public BigDecimal getSemanticWeight() { return semanticWeight; }
    public void setSemanticWeight(BigDecimal semanticWeight) { this.semanticWeight = semanticWeight; }

    public Integer getRankConstant() { return rankConstant; }
    public void setRankConstant(Integer rankConstant) { this.rankConstant = rankConstant; }

    public Integer getCandidateLimit() { return candidateLimit; }
    public void setCandidateLimit(Integer candidateLimit) { this.candidateLimit = candidateLimit; }

    public Integer getResultLimit() { return resultLimit; }
    public void setResultLimit(Integer resultLimit) { this.resultLimit = resultLimit; }

    public Integer getBm25ResultCount() { return bm25ResultCount; }
    public void setBm25ResultCount(Integer bm25ResultCount) { this.bm25ResultCount = bm25ResultCount; }

    public Integer getSemanticResultCount() { return semanticResultCount; }
    public void setSemanticResultCount(Integer semanticResultCount) { this.semanticResultCount = semanticResultCount; }

    public Integer getFinalResultCount() { return finalResultCount; }
    public void setFinalResultCount(Integer finalResultCount) { this.finalResultCount = finalResultCount; }

    public Integer getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(Integer totalCandidates) { this.totalCandidates = totalCandidates; }

    public String getBm25ResultsJson() { return bm25ResultsJson; }
    public void setBm25ResultsJson(String bm25ResultsJson) { this.bm25ResultsJson = bm25ResultsJson; }

    public String getSemanticResultsJson() { return semanticResultsJson; }
    public void setSemanticResultsJson(String semanticResultsJson) { this.semanticResultsJson = semanticResultsJson; }

    public String getFinalResultsJson() { return finalResultsJson; }
    public void setFinalResultsJson(String finalResultsJson) { this.finalResultsJson = finalResultsJson; }

    public String getSettingsJson() { return settingsJson; }
    public void setSettingsJson(String settingsJson) { this.settingsJson = settingsJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
}
