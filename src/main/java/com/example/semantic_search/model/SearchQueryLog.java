package com.example.semantic_search.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kullanıcıların çalıştırdığı her hibrit arama sorgusunu ve sonuç analizini denetim (audit)
 * ve optimizasyon amacıyla saklayan arama kayıt modeli.
 */
public class SearchQueryLog {

    /** Otomatik artan kayıt kimliği. */
    private Long id;

    // ── İstek Bağlamı ────────────────────────────────────────────────────────

    /** Kullanıcının girdiği arama sorgusu. */
    private String query;

    /** Sorgulanan OpenSearch indeksinin adı. */
    private String indexName;

    /** Arama operasyonu türü (örn: HYBRID_EXPLAIN). */
    private String searchType = "HYBRID_EXPLAIN";

    // ── Zamanlama Metrikleri ────────────────────────────────────────────────

    /** Toplam arama süresi (ms). */
    private Long tookMs;

    /** BM25 aramasının sürdüğü süre (ms). */
    private Long bm25TookMs;

    /** Semantik vektör aramasının sürdüğü süre (ms). */
    private Long semanticTookMs;

    // ── Parametreler ve Ağırlıklar ──────────────────────────────────────────

    /** Kullanılan BM25 katsayı ağırlığı. */
    private BigDecimal bm25Weight;

    /** Kullanılan Semantik katsayı ağırlığı. */
    private BigDecimal semanticWeight;

    /** Kullanılan RRF k sabiti. */
    private Integer rankConstant;

    /** Her koldan çekilen aday tavan sayısı. */
    private Integer candidateLimit;

    /** İstenen sonuç limiti. */
    private Integer resultLimit;

    // ── Sonuç Sayıları ──────────────────────────────────────────────────────

    /** BM25 aşamasında bulunan aday adedi. */
    private Integer bm25ResultCount;

    /** Semantik aşamada bulunan aday adedi. */
    private Integer semanticResultCount;

    /** Nihai birleşimde dönen sonuç adedi. */
    private Integer finalResultCount;

    /** Toplanan tekil aday sayısı. */
    private Integer totalCandidates;

    // ── JSON Yükleri ────────────────────────────────────────────────────────

    /** BM25 sonuçlarının tam JSON dökümü. */
    private String bm25ResultsJson;

    /** Semantik arama sonuçlarının tam JSON dökümü. */
    private String semanticResultsJson;

    /** RRF birleşim sonuçlarının tam JSON dökümü. */
    private String finalResultsJson;

    /** İstek parametrelerinin JSON dökümü. */
    private String settingsJson;

    // ── Durum ve Hata ───────────────────────────────────────────────────────

    /** Hata meydana geldiyse hata mesajı. */
    private String errorMessage;

    /** İşlem sonucu durumu (SUCCESS veya ERROR). */
    private String status = "SUCCESS";

    /** Log kaydının oluşturulma zamanı. */
    private Instant createdAt = Instant.now();

    public SearchQueryLog() {
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
