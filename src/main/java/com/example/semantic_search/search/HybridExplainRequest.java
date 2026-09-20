package com.example.semantic_search.search;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Tunable hybrid search request used by the explanation UI. */
public class HybridExplainRequest extends SearchRequest {

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double bm25Weight = 0.5;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double semanticWeight = 0.5;

    @Min(1)
    @Max(1000)
    private Integer rankConstant = 60;

    @Min(1)
    @Max(10)
    private Integer candidateMultiplier = 3;

    @AssertTrue(message = "At least one hybrid search weight must be greater than zero")
    public boolean isWeightConfigurationValid() {
        return valueOrDefault(bm25Weight) + valueOrDefault(semanticWeight) > 0;
    }

    public Double getBm25Weight() { return bm25Weight; }
    public void setBm25Weight(Double bm25Weight) { this.bm25Weight = bm25Weight; }
    public Double getSemanticWeight() { return semanticWeight; }
    public void setSemanticWeight(Double semanticWeight) { this.semanticWeight = semanticWeight; }
    public Integer getRankConstant() { return rankConstant; }
    public void setRankConstant(Integer rankConstant) { this.rankConstant = rankConstant; }
    public Integer getCandidateMultiplier() { return candidateMultiplier; }
    public void setCandidateMultiplier(Integer candidateMultiplier) { this.candidateMultiplier = candidateMultiplier; }

    private String semanticMode = "DENSE";

    public String getSemanticMode() { return semanticMode; }
    public void setSemanticMode(String semanticMode) { this.semanticMode = semanticMode; }

    private double valueOrDefault(Double number) { return number == null ? 0.5 : number; }
}
