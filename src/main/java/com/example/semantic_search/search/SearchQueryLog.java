package com.example.semantic_search.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistent log of every hybrid search explain request.
 * Stores BM25, semantic and RRF results as JSON CLOBs for debugging.
 */
@Entity
@Table(name = "search_query_log")
public class SearchQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "search_query_log_id")
    @SequenceGenerator(name = "search_query_log_id", sequenceName = "search_query_log_seq", allocationSize = 1)
    private Long id;

    // ── Request context ─────────────────────────────────────────────────────

    @Column(nullable = false, length = 2000)
    private String query;

    @Column(name = "index_name", nullable = false, length = 255)
    private String indexName;

    @Column(name = "search_type", nullable = false, length = 50)
    private String searchType = "HYBRID_EXPLAIN";

    // ── Timing ──────────────────────────────────────────────────────────────

    @Column(name = "took_ms")
    private Long tookMs;

    @Column(name = "bm25_took_ms")
    private Long bm25TookMs;

    @Column(name = "semantic_took_ms")
    private Long semanticTookMs;

    // ── Settings ─────────────────────────────────────────────────────────────

    @Column(name = "bm25_weight")
    private BigDecimal bm25Weight;

    @Column(name = "semantic_weight")
    private BigDecimal semanticWeight;

    @Column(name = "rank_constant")
    private Integer rankConstant;

    @Column(name = "candidate_limit")
    private Integer candidateLimit;

    @Column(name = "result_limit")
    private Integer resultLimit;

    // ── Result counts ────────────────────────────────────────────────────────

    @Column(name = "bm25_result_count")
    private Integer bm25ResultCount;

    @Column(name = "semantic_result_count")
    private Integer semanticResultCount;

    @Column(name = "final_result_count")
    private Integer finalResultCount;

    @Column(name = "total_candidates")
    private Integer totalCandidates;

    // ── Full JSON payloads ───────────────────────────────────────────────────

    @Lob
    @Column(name = "bm25_results_json")
    private String bm25ResultsJson;

    @Lob
    @Column(name = "semantic_results_json")
    private String semanticResultsJson;

    @Lob
    @Column(name = "final_results_json")
    private String finalResultsJson;

    @Lob
    @Column(name = "settings_json")
    private String settingsJson;

    // ── Error / status ───────────────────────────────────────────────────────

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    // ── Audit ────────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

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
