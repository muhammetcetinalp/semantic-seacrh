package com.example.semantic_search.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Hibrit arama boru hattı açıklama ve analiz paneli için ayarlanabilir istek DTO'su.
 *
 * <p>Bu sınıf, temel {@link SearchRequest} sınıfını genişleterek hibrit füzyon (RRF),
 * sözcüksel/semantik katsayı ağırlıkları, aday havuzu çarpanı ve anlamsal model modu
 * (Dense veya ColBERT) gibi parametrelerin kullanıcı tarafından dinamik olarak ayarlanabilmesini sağlar.</p>
 */
public class HybridExplainRequest extends SearchRequest {

    /** Sözcüksel arama (BM25) katsayı ağırlığı (0.0 ile 1.0 arasında). */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double bm25Weight = 0.5;

    /** Anlamsal / vektörel arama katsayı ağırlığı (0.0 ile 1.0 arasında). */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double semanticWeight = 0.5;

    /** RRF formülündeki yumuşatma sabiti k (1 ile 1000 arasında, varsayılan: 60). */
    @Min(1)
    @Max(1000)
    private Integer rankConstant = 60;

    /** Nihai limit başına her koldan çekilecek aday sayısı çarpanı (1 ile 10 arasında). */
    @Min(1)
    @Max(10)
    private Integer candidateMultiplier = 3;

    /** Semantik arama modeli modu: DENSE (BGE-M3) veya COLBERT (Token-level MaxSim). */
    private String semanticMode = "DENSE";

    /** Sıralama birleştirme stratejisi: RRF (Reciprocal Rank Fusion) veya SCORE_NORMALIZED. */
    private String fusionMode = "RRF";

    /**
     * Boş yapıcı metot.
     */
    public HybridExplainRequest() {
    }

    /**
     * Hibrit ağırlık konfigürasyonunun geçerliliğini doğrular.
     * En az bir ağırlık (BM25 veya Semantik) sıfırdan büyük olmalıdır.
     *
     * @return Ağırlıklar geçerli ise true, her ikisi de 0 ise false
     */
    @AssertTrue(message = "En az bir hibrit arama ağırlığı (BM25 veya Semantik) sıfırdan büyük olmalıdır")
    public boolean isWeightConfigurationValid() {
        return valueOrDefault(bm25Weight) + valueOrDefault(semanticWeight) > 0;
    }

    /**
     * BM25 ağırlığını döndürür.
     *
     * @return BM25 ağırlık katsayısı
     */
    public Double getBm25Weight() {
        return bm25Weight;
    }

    /**
     * BM25 ağırlığını günceller.
     *
     * @param bm25Weight BM25 ağırlık katsayısı
     */
    public void setBm25Weight(Double bm25Weight) {
        this.bm25Weight = bm25Weight;
    }

    /**
     * Semantik arama ağırlığını döndürür.
     *
     * @return Semantik ağırlık katsayısı
     */
    public Double getSemanticWeight() {
        return semanticWeight;
    }

    /**
     * Semantik arama ağırlığını günceller.
     *
     * @param semanticWeight Semantik ağırlık katsayısı
     */
    public void setSemanticWeight(Double semanticWeight) {
        this.semanticWeight = semanticWeight;
    }

    /**
     * RRF rank sabiti k değerini döndürür.
     *
     * @return Rank sabiti
     */
    public Integer getRankConstant() {
        return rankConstant;
    }

    /**
     * RRF rank sabiti k değerini ayarlar.
     *
     * @param rankConstant Rank sabiti
     */
    public void setRankConstant(Integer rankConstant) {
        this.rankConstant = rankConstant;
    }

    /**
     * Aday havuz çarpanını döndürür.
     *
     * @return Aday çarpanı
     */
    public Integer getCandidateMultiplier() {
        return candidateMultiplier;
    }

    /**
     * Aday havuz çarpanını günceller.
     *
     * @param candidateMultiplier Aday çarpanı
     */
    public void setCandidateMultiplier(Integer candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    /**
     * Seçili semantik modu (DENSE veya COLBERT) döndürür.
     *
     * @return Semantik model modu
     */
    public String getSemanticMode() {
        return semanticMode;
    }

    /**
     * Semantik modu (DENSE veya COLBERT) belirler.
     *
     * @param semanticMode Semantik model modu
     */
    public void setSemanticMode(String semanticMode) {
        this.semanticMode = semanticMode;
    }

    /**
     * Sıralama birleştirme modunu döndürür.
     *
     * @return RRF veya SCORE_NORMALIZED
     */
    public String getFusionMode() {
        return fusionMode;
    }

    /**
     * Sıralama birleştirme modunu günceller.
     *
     * @param fusionMode Füzyon stratejisi
     */
    public void setFusionMode(String fusionMode) {
        this.fusionMode = fusionMode;
    }

    /**
     * Null değer durumunda varsayılan 0.5 katsayısını döner.
     *
     * @param number Sayısal ağırlık
     * @return Sayı değeri veya 0.5
     */
    private double valueOrDefault(Double number) {
        return number == null ? 0.5 : number;
    }
}
